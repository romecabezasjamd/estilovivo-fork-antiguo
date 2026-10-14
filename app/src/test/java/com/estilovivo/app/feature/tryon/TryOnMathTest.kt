package com.estilovivo.app.feature.tryon

import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.TryonLayer
import com.estilovivo.app.core.image.AlphaMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class TryOnMathTest {
    private val sceneH = Scene.height(0.75f)

    private fun layer(id: String = "a", cx: Float = 0.5f, cy: Float = 0.6f, w: Float = 0.4f, cat: TryCategory = TryCategory.Top) =
        TryLayer(id, "g$id", "u$id", "n$id", cx, cy, w, aspect = 1f, category = cat, baseWidth = w)

    private fun finite(l: TryLayer) =
        listOf(l.cx, l.cy, l.width, l.rotation, l.opacity).all { it.isFinite() }

    // ── Transformaciones ───────────────────────────────────────────────────────────────────

    @Test fun `arrastre mueve el centro en unidades de escena`() {
        val r = LayerMath.transform(layer(), sceneH, 0.1f, -0.05f, 1f, 0f, 0.5f, 0.6f)
        assertEquals(0.6f, r.cx, 1e-5f); assertEquals(0.55f, r.cy, 1e-5f); assertEquals(0.4f, r.width, 1e-6f)
    }

    @Test fun `pellizco escala alrededor del centroide`() {
        val l = layer(cx = 0.5f, cy = 0.6f)
        val r = LayerMath.transform(l, sceneH, 0f, 0f, 1.5f, 0f, 0.3f, 0.6f)
        assertEquals(0.6f, r.width, 1e-5f)
        // El punto bajo el pivote no se mueve: distancia al pivote x1.5
        assertEquals(0.3f + 0.2f * 1.5f, r.cx, 1e-5f)
    }

    @Test fun `giro de 90 grados alrededor de un pivote mueve el centro en consecuencia`() {
        val r = LayerMath.transform(layer(cx = 0.6f, cy = 0.5f), sceneH, 0f, 0f, 1f, 90f, 0.5f, 0.5f)
        assertEquals(0.5f, r.cx, 1e-4f); assertEquals(0.6f, r.cy, 1e-4f); assertEquals(90f, r.rotation, 1e-4f)
    }

    @Test fun `la escala se acota entre 0_2x y 5x del tamano inicial`() {
        var l = layer(w = 0.3f)
        repeat(50) { l = LayerMath.transform(l, sceneH, 0f, 0f, 1.3f, 0f, l.cx, l.cy) }
        assertTrue(l.width <= 0.3f * 5f + 1e-4f)
        repeat(100) { l = LayerMath.transform(l, sceneH, 0f, 0f, 0.7f, 0f, l.cx, l.cy) }
        assertTrue(l.width >= maxOf(0.3f * 0.2f, LayerMath.MIN_WIDTH) - 1e-4f)
    }

    @Test fun `la capa no sale del lienzo`() {
        val r = LayerMath.transform(layer(), sceneH, 50f, -50f, 1f, 0f, 0.5f, 0.5f)
        assertEquals(1f, r.cx, 1e-6f); assertEquals(0f, r.cy, 1e-6f)
    }

    @Test fun `entradas no finitas no corrompen el estado`() {
        val l = layer()
        assertEquals(l, LayerMath.transform(l, sceneH, Float.NaN, 0f, 1f, 0f, 0f, 0f))
        val r = LayerMath.transform(l, sceneH, 0f, 0f, Float.NaN, Float.POSITIVE_INFINITY.let { 0f }, 0.5f, 0.5f)
        assertTrue(finite(r))
        assertTrue(finite(LayerMath.transform(l, sceneH, 0f, 0f, 0f, 0f, 0.5f, 0.5f)))
        assertTrue(finite(LayerMath.clampToScene(l.copy(cx = Float.NaN, width = Float.NaN, rotation = Float.POSITIVE_INFINITY), sceneH)))
    }

    @Test fun `rotacion normalizada y ajuste magnetico`() {
        assertEquals(-170f, LayerMath.normalizeDeg(190f), 1e-4f)
        val s = LayerMath.snap(layer(cx = 0.505f).copy(rotation = 88f))
        assertTrue(s.snappedX); assertTrue(s.snappedRotation)
        assertEquals(0.5f, s.layer.cx, 1e-6f); assertEquals(90f, s.layer.rotation, 1e-4f)
        assertFalse(LayerMath.snap(layer(cx = 0.7f).copy(rotation = 30f)).snappedX)
    }

    @Test fun `asa de esquina escala y gira respecto al centro`() {
        val l = layer(cx = 0.5f, cy = 0.5f)
        val r = LayerMath.handleDrag(l, 0.7f, 0.5f, 0.5f, 0.9f, sceneH)
        assertEquals(0.4f * 2f, r.width, 1e-4f)
        assertEquals(90f, r.rotation, 1e-3f)
    }

    // ── Orden Z / duplicado / historial ────────────────────────────────────────────────────

    @Test fun `orden Z`() {
        val l = listOf(layer("a"), layer("b"), layer("c"))
        assertEquals(listOf("b", "c", "a"), LayerMath.bringToFront(l, "a").map { it.id })
        assertEquals(listOf("c", "a", "b"), LayerMath.sendToBack(l, "c").map { it.id })
        assertEquals(listOf("a", "c", "b"), LayerMath.moveForward(l, "b").map { it.id })
        assertEquals(listOf("b", "a", "c"), LayerMath.moveBackward(l, "b").map { it.id })
        assertEquals(l, LayerMath.bringToFront(l, "c"))
        assertEquals(l, LayerMath.sendToBack(l, "a"))
    }

    @Test fun `insercion por rango deja pantalon debajo y abrigo encima`() {
        var l = listOf<TryLayer>()
        l = LayerMath.insertByRank(l, layer("top", cat = TryCategory.Top))
        l = LayerMath.insertByRank(l, layer("bottom", cat = TryCategory.Bottom))
        l = LayerMath.insertByRank(l, layer("outer", cat = TryCategory.Outer))
        l = LayerMath.insertByRank(l, layer("shoes", cat = TryCategory.Shoes))
        l = LayerMath.insertByRank(l, layer("hat", cat = TryCategory.Accessory))
        assertEquals(listOf("bottom", "shoes", "top", "outer", "hat"), l.map { it.id })
    }

    @Test fun `duplicar crea capa nueva encima y desplazada`() {
        val l = LayerMath.duplicate(listOf(layer("a").copy(locked = true)), "a", "b")
        assertEquals(2, l.size); assertEquals("b", l.last().id); assertFalse(l.last().locked)
        assertTrue(l.last().cx > l.first().cx)
    }

    @Test fun `deshacer y rehacer`() {
        var st = UndoStack<List<Int>>()
        var cur = listOf(1)
        st = st.record(cur); cur = listOf(1, 2)
        st = st.record(cur); cur = listOf(1, 2, 3)
        val (s1, v1) = st.undo(cur)!!; assertEquals(listOf(1, 2), v1)
        val (s2, v2) = s1.undo(v1)!!; assertEquals(listOf(1), v2)
        assertNull(s2.undo(v2))
        val (s3, v3) = s2.redo(v2)!!; assertEquals(listOf(1, 2), v3)
        assertTrue(s3.canRedo)
        assertFalse(s3.record(v3).canRedo) // un cambio nuevo descarta el futuro
    }

    @Test fun `el historial esta acotado`() {
        var st = UndoStack<Int>()
        repeat(200) { st = st.record(it) }
        assertEquals(UndoStack.LIMIT, st.past.size)
    }

    // ── Hit-test con alfa ──────────────────────────────────────────────────────────────────

    /** Imagen 8x8 con sólo la mitad izquierda opaca. */
    private fun leftHalfMask(): AlphaMask {
        val px = IntArray(64) { i -> if (i % 8 < 4) 0xFF000000.toInt() else 0 }
        return AlphaMask.fromPixels(px, 8, 8)
    }

    @Test fun `tocar un hueco transparente no selecciona la capa`() {
        val l = layer(cx = 0.5f, cy = 0.5f, w = 0.4f)
        val mask = leftHalfMask()
        assertNotNull(LayerMath.hitTest(listOf(l), 0.4f, 0.5f) { mask })      // mitad izquierda: opaca
        assertNull(LayerMath.hitTest(listOf(l), 0.6f, 0.5f) { mask })         // mitad derecha: hueco
        assertNotNull(LayerMath.hitTest(listOf(l), 0.6f, 0.5f))               // sin máscara: rectángulo
    }

    @Test fun `el volteo horizontal invierte el hit-test`() {
        val l = layer(cx = 0.5f, cy = 0.5f, w = 0.4f).copy(flipX = true)
        val mask = leftHalfMask()
        assertNull(LayerMath.hitTest(listOf(l), 0.4f, 0.5f) { mask })
        assertNotNull(LayerMath.hitTest(listOf(l), 0.6f, 0.5f) { mask })
    }

    @Test fun `el hit-test respeta la prioridad Z y salta a la de abajo si arriba hay hueco`() {
        val bottom = layer("bottom", w = 0.4f)
        val top = layer("top", w = 0.4f)
        val solid = AlphaMask.solid()
        val hole = leftHalfMask()
        assertEquals("top", LayerMath.hitTest(listOf(bottom, top), 0.5f, 0.5f) { solid }!!.id)
        assertEquals("bottom", LayerMath.hitTest(listOf(bottom, top), 0.6f, 0.5f) { if (it.id == "top") hole else solid }!!.id)
    }

    @Test fun `hit-test con rotacion`() {
        val l = layer(cx = 0.5f, cy = 0.5f, w = 0.4f).copy(aspect = 0.25f, rotation = 90f) // 0.4 x 0.1 girada 90
        assertTrue(LayerMath.contains(l, 0.5f, 0.65f))
        assertFalse(LayerMath.contains(l, 0.65f, 0.5f))
    }

    // ── Estabilidad ────────────────────────────────────────────────────────────────────────

    @Test fun `10000 gestos aleatorios mantienen el estado finito y acotado`() {
        val rnd = Random(42)
        var l = layer()
        var v = ViewState()
        fun r(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
        repeat(10_000) {
            val zoom = when (rnd.nextInt(6)) { 0 -> 0f; 1 -> Float.NaN; 2 -> r(0.01f, 100f); else -> r(0.8f, 1.25f) }
            val pan = r(-3f, 3f)
            val rot = if (rnd.nextInt(50) == 0) Float.NaN else r(-40f, 40f)
            l = LayerMath.snap(LayerMath.transform(l, sceneH, pan, r(-3f, 3f), zoom, rot, r(-1f, 2f), r(-1f, 2f))).layer
            v = ViewMath.apply(v, zoom, r(-500f, 500f), r(-500f, 500f), r(-400f, 400f), r(-400f, 400f), 800f, sceneH, 1080f, 1700f)
            assertTrue(finite(l))
            assertTrue(l.cx in 0f..1f && l.cy in 0f..sceneH)
            assertTrue(l.width in LayerMath.widthRange(l))
            assertTrue(abs(l.rotation) <= 180.0001f)
            assertTrue(v.zoom in ViewMath.MIN_ZOOM..ViewMath.MAX_ZOOM)
            assertTrue(v.panX.isFinite() && v.panY.isFinite() && abs(v.panX) < 20_000f && abs(v.panY) < 20_000f)
        }
    }

    @Test fun `sin deriva acumulada ida y vuelta del mismo gesto`() {
        var l = layer()
        val start = l
        repeat(500) { l = LayerMath.transform(l, sceneH, 0.001f, 0f, 1.001f, 0.1f, l.cx, l.cy) }
        repeat(500) { l = LayerMath.transform(l, sceneH, -0.001f, 0f, 1f / 1.001f, -0.1f, l.cx, l.cy) }
        assertEquals(start.width, l.width, 5e-3f)
        assertEquals(start.rotation, l.rotation, 1e-2f)
    }

    @Test fun `ViewMath mantiene el punto bajo los dedos al hacer zoom`() {
        val v = ViewMath.apply(ViewState(), 2f, 0f, 0f, 100f, 50f, 800f, sceneH, 1080f, 1700f)
        assertEquals(2f, v.zoom, 1e-5f)
        // El punto a (100,50) del centro se queda donde estaba: pan = o - (o - 0)*2 = -o
        assertEquals(-100f, v.panX, 1e-3f); assertEquals(-50f, v.panY, 1e-3f)
        assertEquals(ViewState(), ViewMath.apply(ViewState(), Float.NaN, Float.NaN, 0f, 0f, 0f, 800f, sceneH, 1080f, 1700f))
    }

    // ── Presets / códec ────────────────────────────────────────────────────────────────────

    @Test fun `ida y vuelta de capas del servidor`() {
        val l = layer("x", cx = 0.4f, cy = 0.7f, w = 0.35f).copy(aspect = 1.4f, rotation = 12f, opacity = 0.8f, flipX = true, locked = true)
        val api = LayerCodec.toApi(l, sceneH)
        assertEquals(l.cx - l.width / 2f, api.x, 1e-6f)
        val back = LayerCodec.fromApi(api, sceneH, "new")!!
        assertEquals(l.cx, back.cx, 1e-4f); assertEquals(l.cy, back.cy, 1e-4f); assertEquals(l.width, back.width, 1e-5f)
        assertEquals(l.aspect, back.aspect, 1e-3f); assertEquals(12f, back.rotation, 1e-5f)
        assertEquals(0.8f, back.opacity, 1e-6f); assertTrue(back.flipX && back.locked)
        assertEquals("new", back.id)
    }

    @Test fun `capas antiguas en pixeles de la web se normalizan`() {
        val legacy = TryonLayer("1", "g1", "u", x = 100f, y = 150f, w = 200f, h = 260f, rotation = 5f, opacity = 1f)
        val l = LayerCodec.fromApi(legacy, sceneH, "n")!!
        assertEquals(0.5f, l.width, 1e-5f)
        assertEquals(1.3f, l.aspect, 1e-5f)
        assertTrue(l.cx in 0f..1f && l.cy in 0f..sceneH)
    }

    @Test fun `capas inservibles se descartan y las prendas borradas se omiten con aviso`() {
        assertNull(LayerCodec.fromApi(TryonLayer("1", "", "u", w = 0.3f, h = 0.3f), sceneH, "n"))
        assertNull(LayerCodec.fromApi(TryonLayer("1", "g", "u", w = 0f, h = 0.3f), sceneH, "n"))
        val g = Garment("g1", "https://img/new.jpg", "Camisa", "top", "negro", "all", 0)
        val api = listOf(
            LayerCodec.toApi(layer("a").copy(garmentId = "g1"), sceneH),
            LayerCodec.toApi(layer("b").copy(garmentId = "gone"), sceneH),
        )
        var n = 0
        val r = LayerCodec.resolve(api, listOf(g), sceneH) { "id${n++}" }
        assertEquals(1, r.layers.size); assertEquals(1, r.missing)
        assertEquals("https://img/new.jpg", r.layers[0].url) // usa la URL actual de la prenda
        assertEquals(TryCategory.Top, r.layers[0].category)
    }

    @Test fun `borrador sobrevive a ida y vuelta y se sanea`() {
        val d = TryDraft(bodyPath = "/x/body.jpg", bodyAspect = 0.6f, layers = listOf(layer("a"), layer("b").copy(cutout = -1)), mirror = true)
        val back = DraftCodec.decode(DraftCodec.encode(d))!!
        assertEquals(2, back.layers.size); assertEquals(-1, back.layers[1].cutout); assertTrue(back.mirror)
        assertNull(DraftCodec.decode("{ no es json"))
        assertNull(DraftCodec.decode(null))
        val bad = DraftCodec.encode(d.copy(layers = listOf(layer("a").copy(url = ""), layer("b"))))
        assertEquals(1, DraftCodec.decode(bad)!!.layers.size)
    }

    @Test fun `reglas de presets`() {
        assertEquals("a b", PresetRules.cleanName("  a   b  "))
        assertEquals(80, PresetRules.cleanName("x".repeat(200)).length)
        assertEquals("Fiesta", PresetRules.occasionLabel("party"))
        assertEquals(75, PresetRules.dataUrlBytes("data:image/jpeg;base64," + "A".repeat(100)))
    }

    // ── Colocación / sorpréndeme / pose ────────────────────────────────────────────────────

    @Test fun `categorias`() {
        assertEquals(TryCategory.Shoes, TryCatalog.categoryOf("Zapatillas deportivas".replace("Zapatillas", "zapato")))
        assertEquals(TryCategory.Dress, TryCatalog.categoryOf("vestido"))
        assertEquals(TryCategory.Outer, TryCatalog.categoryOf("outerwear"))
        assertEquals(TryCategory.Bottom, TryCatalog.categoryOf("bottom"))
        assertEquals(TryCategory.Top, TryCatalog.categoryOf("top"))
        assertEquals(TryCategory.Accessory, TryCatalog.categoryOf("accessories"))
    }

    @Test fun `colocacion automatica respeta la altura maxima y queda dentro del lienzo`() {
        TryCategory.entries.forEach { c ->
            listOf(0.2f, 1f, 3f, 6f).forEach { asp ->
                val p = AutoPlacer.place(c, asp, sceneH)
                assertTrue(p.width >= LayerMath.MIN_WIDTH && p.cx == 0.5f && p.cy in 0f..sceneH)
            }
        }
    }

    private fun garment(id: String, type: String) = Garment(id, "https://img/$id.jpg", id, type, "negro", "all", 0)

    @Test fun `sorprendeme arma look con una prenda por zona`() {
        val all = listOf(garment("t", "top"), garment("b", "bottom"), garment("s", "shoes"), garment("d", "dress"))
        repeat(20) { seed ->
            val out = OutfitBuilder.build(all, Random(seed))
            assertTrue(out.isNotEmpty())
            val cats = out.map { TryCatalog.categoryOf(it.type) }
            assertEquals(cats.size, cats.toSet().size)
            assertTrue(TryCategory.Shoes in cats)
            assertTrue(TryCategory.Dress in cats || (TryCategory.Top in cats && TryCategory.Bottom in cats))
        }
        assertTrue(OutfitBuilder.build(emptyList(), Random(1)).isEmpty())
        assertTrue(OutfitBuilder.build(listOf(garment("w", "top").copy(isWashing = true)), Random(1)).isEmpty())
    }

    private fun pose(): BodyLandmarks = BodyLandmarks(
        nose = Pt(0.5f, 0.15f),
        lShoulder = Pt(0.4f, 0.25f), rShoulder = Pt(0.6f, 0.25f), lHip = Pt(0.43f, 0.55f), rHip = Pt(0.57f, 0.55f),
        lKnee = Pt(0.43f, 0.75f), rKnee = Pt(0.57f, 0.75f), lAnkle = Pt(0.43f, 0.93f), rAnkle = Pt(0.57f, 0.93f),
    )

    @Test fun `ajuste por pose coloca la camiseta entre hombros y cadera`() {
        val lm = pose().toScene(0.75f, 0.75f)
        val f = PoseFitter.fit(TryCategory.Top, "camiseta", 1f, lm, sceneH)!!
        val sw = 0.2f // ancho de hombros en unidades de escena (la foto llena el ancho)
        assertEquals(sw * 1.3f, f.width, 0.02f)
        assertEquals(0.5f, f.cx, 0.01f)
        assertTrue(f.cy > lm.lShoulder!!.y && f.cy < lm.lHip!!.y + 0.1f)
        assertEquals(0f, f.rotation, 0.5f)
    }

    @Test fun `ajuste por pose de pantalon zapatos y vestido`() {
        val lm = pose().toScene(0.75f, 0.75f)
        val bottom = PoseFitter.fit(TryCategory.Bottom, "vaquero", 1.6f, lm, sceneH)!!
        assertTrue(bottom.cy > lm.lHip!!.y && bottom.cy < lm.lAnkle!!.y)
        val shoes = PoseFitter.fit(TryCategory.Shoes, "zapato", 0.5f, lm, sceneH)!!
        assertTrue(shoes.cy >= lm.lAnkle!!.y)
        val dress = PoseFitter.fit(TryCategory.Dress, "vestido", 1.5f, lm, sceneH)!!
        assertTrue(dress.width >= (lm.rShoulder!!.x - lm.lShoulder!!.x) * 1.1f - 1e-4f)
    }

    @Test fun `sin pose fiable no se ajusta`() {
        assertNull(PoseFitter.fit(TryCategory.Top, "x", 1f, BodyLandmarks(), sceneH))
        val weak = pose().map { it.copy(conf = 0.1f) }
        assertNull(PoseFitter.fit(TryCategory.Top, "x", 1f, weak, sceneH))
        assertFalse(weak.usable)
    }

    @Test fun `un tronco inclinado gira la prenda con limite`() {
        val tilted = pose().copy(lHip = Pt(0.55f, 0.55f), rHip = Pt(0.69f, 0.55f)).toScene(0.75f, 0.75f)
        val f = PoseFitter.fit(TryCategory.Top, "camiseta", 1f, tilted, sceneH)!!
        assertTrue(abs(f.rotation) <= 25f && f.rotation != 0f)
    }

    @Test fun `la curva de muelle arranca en 0 y termina en 1`() {
        assertEquals(0f, FitSpring.value(0f), 1e-6f)
        assertEquals(1f, FitSpring.value(0.9f), 1e-6f)
        assertTrue((1..49).any { FitSpring.value(it * 0.016f) > 1f }) // rebote
    }
}
