package com.estilovivo.app.feature.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Coordinates(val lat: Double, val lon: Double)

/** De dónde sale la previsión. Equivale al orden de la web: ciudad del perfil, ubicación del dispositivo, Madrid. */
sealed interface WeatherLocation {
    data class City(val name: String) : WeatherLocation
    data class Device(val coords: Coordinates) : WeatherLocation
    data object Default : WeatherLocation
}

data class GeoPlace(val name: String, val coords: Coordinates)
data class RawWeather(val temp: Double, val humidity: Int, val code: Int, val windKmh: Double)

/** Origen de datos (Open-Meteo). Lanza [IOException] ante fallos de red o respuestas inválidas. */
interface WeatherApi {
    suspend fun geocode(name: String): GeoPlace?
    suspend fun forecast(coords: Coordinates): RawWeather
}

/**
 * Tiempo actual con caché en memoria de 30 min por ubicación. Si la red falla devuelve la última lectura
 * (marcada `stale`) y, si no hay ninguna, `null`: la UI muestra "sin datos" sin bloquear nada.
 */
class WeatherRepository(
    private val api: WeatherApi,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = TTL_MS,
) {
    private data class Entry(val at: Long, val data: WeatherData)

    private val cache = HashMap<String, Entry>()
    private val geocodeCache = HashMap<String, GeoPlace?>()
    private val mutex = Mutex()

    suspend fun load(location: WeatherLocation, force: Boolean = false): WeatherData? = mutex.withLock {
        val key = keyOf(location)
        val cached = cache[key]
        if (!force && cached != null && nowMs() - cached.at in 0 until ttlMs) return cached.data
        try {
            val (coords, place) = resolve(location)
            val raw = api.forecast(coords)
            val temp = Math.round(raw.temp).toInt()
            val data = WeatherData(
                temp = temp,
                condition = WeatherLogic.condition(raw.code, raw.temp, raw.windKmh),
                humidity = raw.humidity,
                description = WeatherLogic.description(raw.code),
                place = place,
            )
            cache[key] = Entry(nowMs(), data)
            data
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cached?.data?.copy(stale = true)
        }
    }

    private suspend fun resolve(location: WeatherLocation): Pair<Coordinates, String> = when (location) {
        is WeatherLocation.Device -> location.coords to "Tu ubicación"
        WeatherLocation.Default -> MADRID.coords to MADRID.name
        is WeatherLocation.City -> {
            val name = location.name.trim()
            val place = if (geocodeCache.containsKey(name.lowercase())) {
                geocodeCache[name.lowercase()]
            } else {
                // Un fallo de red al geocodificar no se cachea; "ciudad no encontrada" sí.
                val found = api.geocode(name)
                geocodeCache[name.lowercase()] = found
                found
            }
            if (place != null) place.coords to place.name else MADRID.coords to MADRID.name
        }
    }

    private fun keyOf(location: WeatherLocation): String = when (location) {
        is WeatherLocation.City -> "city:${location.name.trim().lowercase()}"
        is WeatherLocation.Device -> "dev:%.2f,%.2f".format(java.util.Locale.US, location.coords.lat, location.coords.lon)
        WeatherLocation.Default -> "default"
    }

    companion object {
        const val TTL_MS = 30L * 60L * 1000L
        val MADRID = GeoPlace("Madrid", Coordinates(40.4168, -3.7038))
    }
}

/** Cliente de Open-Meteo (sin clave, sin credenciales). Usa su propio OkHttp: jamás el de la API con el token. */
class OpenMeteoApi(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : WeatherApi {
    override suspend fun geocode(name: String): GeoPlace? {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", name)
            .addQueryParameter("count", "1")
            .addQueryParameter("language", "es")
            .build()
        val body = get(url.toString())
        val first = (body["results"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull()?.jsonObject ?: return null
        val lat = first["latitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = first["longitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val label = first["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: name
        return GeoPlace(label, Coordinates(lat, lon))
    }

    override suspend fun forecast(coords: Coordinates): RawWeather {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", coords.lat.toString())
            .addQueryParameter("longitude", coords.lon.toString())
            .addQueryParameter("current", "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m")
            .addQueryParameter("timezone", "auto")
            .build()
        val current = get(url.toString())["current"]?.jsonObject ?: throw IOException("Respuesta sin datos")
        val temp = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull ?: throw IOException("Respuesta sin temperatura")
        return RawWeather(
            temp = temp,
            humidity = current["relative_humidity_2m"]?.jsonPrimitive?.intOrNull ?: 0,
            code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: 0,
            windKmh = current["wind_speed_10m"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
        )
    }

    private suspend fun get(url: String): JsonObject {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        val text = client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }
        return try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw IOException("Respuesta no válida", e)
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}

/** Ubicación aproximada del dispositivo (permiso `ACCESS_COARSE_LOCATION`), sin Play Services. */
interface DeviceLocationProvider {
    fun hasPermission(): Boolean
    suspend fun current(): Coordinates?
}

class AndroidLocationProvider(private val context: Context) : DeviceLocationProvider {
    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun current(): Coordinates? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) || it == LocationManager.PASSIVE_PROVIDER }.getOrDefault(false) }
        // 1) Última posición conocida (instantánea).
        val last = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 6 * 60 * 60 * 1000L }
            .maxByOrNull { it.time }
        if (last != null) return Coordinates(last.latitude, last.longitude)
        // 2) Una lectura nueva con tiempo límite (API 30+).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return null
        val fresh = withTimeoutOrNull(6_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                } catch (e: RuntimeException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        return fresh?.let { Coordinates(it.latitude, it.longitude) }
    }
}
