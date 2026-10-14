package com.estilovivo.app.feature.wardrobe

import android.graphics.Bitmap
import com.estilovivo.app.core.image.CutoutMethod
import com.estilovivo.app.core.image.CutoutOptions
import com.estilovivo.app.core.image.CutoutResult
import com.estilovivo.app.core.image.GarmentCutout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bitmap "hueco" para JVM: sólo se pasa de mano en mano, nunca se llama a sus métodos. */
private fun fakeBitmap(): Bitmap {
    val f = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
    return (f.get(null) as sun.misc.Unsafe).allocateInstance(Bitmap::class.java) as Bitmap
}

private class FakeCutout(var result: suspend () -> CutoutResult) : GarmentCutout {
    var calls = 0
    var lastOptions: CutoutOptions? = null
    override suspend fun cutOut(src: Bitmap, options: CutoutOptions): CutoutResult {
        calls++; lastOptions = options
        return result()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoCutoutTest {
    private val src = fakeBitmap()
    private val out = fakeBitmap()
    private var stored = 0

    private fun decider(
        cutout: GarmentCutout,
        load: suspend (String) -> Bitmap? = { src },
        store: suspend (String, Bitmap) -> String? = { _, _ -> stored++; "file:///c/cutout_x.png" },
        cached: (String) -> String? = { null },
    ) = PhotoCutoutDecider(load, cutout, store, cached)

    private val ok = { CutoutResult.Success(out, CutoutMethod.MlKit, 0.4f) }

    @Test fun `activado y con exito usa el recorte guardado`() = runTest {
        val fake = FakeCutout { ok() }
        val r = decider(fake).prepare("content://p/1", enabled = true)
        assertEquals(CutoutOutcome.Applied("file:///c/cutout_x.png", CutoutMethod.MlKit), r)
        assertEquals(1, stored)
        assertTrue(fake.lastOptions!!.crop)
    }

    @Test fun `desactivado usa la original sin recortar`() = runTest {
        val fake = FakeCutout { ok() }
        val r = decider(fake).prepare("content://p/1", enabled = false)
        assertEquals(CutoutOutcome.Original(null), r)
        assertEquals(0, fake.calls)
    }

    @Test fun `rechazo vuelve a la original con el motivo`() = runTest {
        val r = decider(FakeCutout { CutoutResult.Rejected("casi vacio", 0.01f) }).prepare("content://p/1", true)
        assertEquals(CutoutOutcome.Original("casi vacio"), r)
        assertEquals(0, stored)
    }

    @Test fun `fallo vuelve a la original con aviso`() = runTest {
        val r = decider(FakeCutout { CutoutResult.Failed("sin red") }).prepare("content://p/1", true)
        assertEquals(CutoutOutcome.Original("sin red"), r)
    }

    @Test fun `una excepcion inesperada nunca bloquea el alta`() = runTest {
        val r = decider(FakeCutout { error("boom") }).prepare("content://p/1", true)
        assertEquals(CutoutOutcome.Original(CUTOUT_UNAVAILABLE_NOTE), r)
    }

    @Test fun `no poder leer la foto o guardar el recorte vuelve a la original`() = runTest {
        val fake = FakeCutout { ok() }
        assertEquals(CutoutOutcome.Original(CUTOUT_UNAVAILABLE_NOTE), decider(fake, load = { null }).prepare("u", true))
        assertEquals(CutoutOutcome.Original(CUTOUT_UNAVAILABLE_NOTE), decider(fake, store = { _, _ -> null }).prepare("u", true))
    }

    @Test fun `la cache evita recortar dos veces`() = runTest {
        val fake = FakeCutout { ok() }
        val r = decider(fake, cached = { "file:///c/cutout_cached.png" }).prepare("u", true)
        assertTrue(r is CutoutOutcome.Applied)
        assertEquals(0, fake.calls)
    }

    @Test fun `un recorte ya guardado no se vuelve a recortar`() = runTest {
        val fake = FakeCutout { ok() }
        val r = decider(fake).prepare("file:///data/cache/images/cutout_abc.png", true)
        assertEquals(CutoutOutcome.Applied("file:///data/cache/images/cutout_abc.png", null), r)
        assertEquals(0, fake.calls)
    }

    @Test fun `cambiar de foto cancela el recorte en curso sin guardar nada`() = runTest(UnconfinedTestDispatcher()) {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val fake = FakeCutout {
            started.complete(Unit)
            try { awaitCancellation() } catch (e: kotlinx.coroutines.CancellationException) { cancelled = true; throw e }
        }
        val d = decider(fake)
        val job = async(start = CoroutineStart.UNDISPATCHED) { d.prepare("content://p/1", true) }
        started.await()
        job.cancelAndJoin()
        assertTrue(cancelled)
        assertTrue(job.isCancelled)
        assertEquals(0, stored)
        // La foto nueva se procesa con normalidad.
        fake.result = { ok() }
        assertTrue(d.prepare("content://p/2", true) is CutoutOutcome.Applied)
    }

    @Test fun `limite de tamano reescala solo cuando hace falta`() {
        assertNull(CutoutSize.scaleFor(CutoutSize.MAX_BYTES))
        val s = CutoutSize.scaleFor(CutoutSize.MAX_BYTES * 4)!!
        assertTrue(s in 0.3f..0.95f)
        assertEquals(0.45f, s, 0.01f)
        assertEquals(0.3f, CutoutSize.scaleFor(Int.MAX_VALUE)!!, 0.001f)
    }

    @Test fun `reconoce los recortes propios`() {
        assertTrue(PreparedPhoto.isPrepared("file:///x/images/cutout_1.png"))
        assertFalse(PreparedPhoto.isPrepared("file:///x/images/garment_1.jpg"))
        assertFalse(PreparedPhoto.isPrepared("content://media/cutout_1.png"))
        assertFalse(PreparedPhoto.isPrepared(null))
    }
}
