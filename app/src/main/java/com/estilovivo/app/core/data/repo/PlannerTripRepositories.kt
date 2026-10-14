package com.estilovivo.app.core.data.repo

import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.NewTripItem
import com.estilovivo.app.core.data.model.PlannerEntry
import com.estilovivo.app.core.data.model.Trip
import com.estilovivo.app.core.data.model.TripItem
import com.estilovivo.app.core.data.net.ApiService
import com.estilovivo.app.core.data.net.Mappers
import com.estilovivo.app.core.data.net.PlannerRequest
import com.estilovivo.app.core.data.net.TripItemRequest
import com.estilovivo.app.core.data.net.TripRequest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Planificador de outfits por día. */
interface PlannerRepository {
    /** Todas las entradas del planificador (`date` = `YYYY-MM-DD`). */
    suspend fun getAll(): ApiResult<List<PlannerEntry>>

    /** Crea o actualiza el día. [lookId] = null deja el día sin look (sólo nota). */
    suspend fun save(date: String, lookId: String?, eventNote: String = ""): ApiResult<PlannerEntry>

    suspend fun delete(date: String): ApiResult<Unit>
}

internal class PlannerRepositoryImpl(private val api: ApiService, private val mappers: Mappers) : PlannerRepository {
    override suspend fun getAll() = apiCall { api.planner().map(mappers::plannerEntry) }

    override suspend fun save(date: String, lookId: String?, eventNote: String) = apiCall {
        mappers.plannerEntry(api.savePlannerEntry(PlannerRequest(date, lookId?.takeIf { it.isNotEmpty() }, eventNote)))
    }

    override suspend fun delete(date: String) = apiCall { api.deletePlannerEntry(date); Unit }
}

/** Viajes y lista de equipaje. */
interface TripRepository {
    suspend fun getAll(): ApiResult<List<Trip>>

    /** Crea un viaje con su lista inicial. Fechas `YYYY-MM-DD` (la vuelta no puede ser anterior a la ida). */
    suspend fun create(
        destination: String,
        dateStart: String,
        dateEnd: String,
        items: List<NewTripItem> = emptyList(),
        garmentIds: List<String> = emptyList(),
    ): ApiResult<Trip>

    /** Sólo datos del viaje (destino, fechas y prendas). Los elementos se gestionan con [addItem]/[updateItem]/[deleteItem]. */
    suspend fun updateMeta(
        tripId: String,
        destination: String,
        dateStart: String,
        dateEnd: String,
        garmentIds: List<String>,
    ): ApiResult<Trip>

    suspend fun delete(tripId: String): ApiResult<Unit>
    suspend fun addItem(tripId: String, label: String, isEssential: Boolean = false): ApiResult<TripItem>

    /** Cambios parciales (null = no tocar). */
    suspend fun updateItem(
        tripId: String,
        itemId: String,
        checked: Boolean? = null,
        label: String? = null,
        isEssential: Boolean? = null,
    ): ApiResult<TripItem>

    suspend fun deleteItem(tripId: String, itemId: String): ApiResult<Unit>
}

internal class TripRepositoryImpl(private val api: ApiService, private val mappers: Mappers) : TripRepository {
    override suspend fun getAll() = apiCall { api.trips().map(mappers::trip) }

    override suspend fun create(destination: String, dateStart: String, dateEnd: String, items: List<NewTripItem>, garmentIds: List<String>) =
        apiCall {
            mappers.trip(
                api.createTrip(
                    TripRequest(
                        destination.trim(), dateStart, dateEnd,
                        items = items.map { TripItemRequest(it.label, it.checked, it.isEssential) },
                        garmentIds = garmentIds,
                    ),
                ),
            )
        }

    override suspend fun updateMeta(tripId: String, destination: String, dateStart: String, dateEnd: String, garmentIds: List<String>) =
        apiCall { mappers.trip(api.updateTrip(tripId, TripRequest(destination.trim(), dateStart, dateEnd, garmentIds = garmentIds))) }

    override suspend fun delete(tripId: String) = apiCall { api.deleteTrip(tripId); Unit }

    override suspend fun addItem(tripId: String, label: String, isEssential: Boolean) = apiCall {
        mappers.tripItem(api.addTripItem(tripId, buildJsonObject { put("label", label); put("isEssential", isEssential) }))
    }

    override suspend fun updateItem(tripId: String, itemId: String, checked: Boolean?, label: String?, isEssential: Boolean?) = apiCall {
        mappers.tripItem(
            api.updateTripItem(
                tripId, itemId,
                buildJsonObject {
                    checked?.let { put("checked", it) }
                    label?.let { put("label", it) }
                    isEssential?.let { put("isEssential", it) }
                },
            ),
        )
    }

    override suspend fun deleteItem(tripId: String, itemId: String) = apiCall { api.deleteTripItem(tripId, itemId); Unit }
}
