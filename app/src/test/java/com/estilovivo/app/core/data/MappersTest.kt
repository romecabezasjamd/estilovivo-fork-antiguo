package com.estilovivo.app.core.data

import com.estilovivo.app.core.data.net.ApiJson
import com.estilovivo.app.core.data.net.AssetUrls
import com.estilovivo.app.core.data.net.ApiConfig
import com.estilovivo.app.core.data.net.ConversationDto
import com.estilovivo.app.core.data.net.LookDto
import com.estilovivo.app.core.data.net.Mappers
import com.estilovivo.app.core.data.net.PageDto
import com.estilovivo.app.core.data.net.ProductDto
import com.estilovivo.app.core.data.net.PublicProfileDto
import com.estilovivo.app.core.data.net.StoryDto
import com.estilovivo.app.core.data.net.TripDto
import com.estilovivo.app.core.data.net.UserDto
import com.estilovivo.app.core.data.repo.fetchAllPages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {
    private val origin = "https://example.test"
    private val mappers = Mappers(AssetUrls(origin))

    private inline fun <reified T> parse(json: String): T = ApiJson.decodeFromString(json)

    @Test fun `ApiConfig normaliza la base con o sin api`() {
        assertEquals("https://example.test", ApiConfig("https://example.test").origin)
        assertEquals("https://example.test", ApiConfig("https://example.test/api/").origin)
        assertEquals("https://example.test/api/", ApiConfig("https://example.test/").apiBaseUrl)
        assertEquals("example.test", ApiConfig("https://example.test").host)
    }

    @Test fun `resolveAssetUrl replica la web`() {
        val a = AssetUrls(origin)
        assertNull(a.resolve(null))
        assertNull(a.resolve(""))
        assertEquals("https://example.test/api/uploads/x.webp", a.resolve("/api/uploads/x.webp"))
        assertEquals("https://example.test/api/uploads/x.webp", a.resolve("api/uploads/x.webp"))
        assertEquals("https://cdn.test/a.png", a.resolve("https://cdn.test/a.png"))
        assertEquals("data:image/png;base64,AAA", a.resolve("data:image/png;base64,AAA"))
        assertEquals("/api/uploads/x.webp", a.toRelative("https://example.test/api/uploads/x.webp"))
    }

    @Test fun `mapProductToGarment con todos los campos`() {
        val p: ProductDto = parse(
            """{"id":"p1","name":"Camisa","category":"top","color":"azul","season":"summer","usageCount":3,
               "lastWorn":"2026-01-02T00:00:00.000Z","forSale":true,"price":12.5,"brand":"Zara","size":"M","condition":"good",
               "description":"Bonita","isWashing":true,"userId":"u1","user":{"id":"u1","name":"Ana","avatar":"/api/uploads/a.webp"},
               "images":[{"url":"/api/uploads/p1.webp"}],"extra":"ignorado"}""",
        )
        val g = mappers.garment(p)!!
        assertEquals("https://example.test/api/uploads/p1.webp", g.imageUrl)
        assertEquals("Camisa", g.name)
        assertEquals("top", g.type)
        assertEquals(3, g.usageCount)
        assertEquals(12.5, g.price, 0.0)
        assertTrue(g.forSale)
        assertTrue(g.isWashing)
        assertEquals("u1", g.userId)
        assertEquals("Ana", g.userName)
        assertEquals("https://example.test/api/uploads/a.webp", g.userAvatar)
    }

    @Test fun `mapProductToGarment aplica los valores por defecto de la web`() {
        val g = mappers.garment(parse<ProductDto>("""{"id":"p2","category":"shoes"}"""))!!
        assertEquals("shoes", g.name) // name || category
        assertEquals("varios", g.color)
        assertEquals("all", g.season)
        assertEquals("new", g.condition)
        assertEquals(0.0, g.price, 0.0)
        assertEquals("https://example.test/api/uploads/placeholder.png", g.imageUrl)
        assertFalse(g.isWashing)
        assertNull(g.brand)
        assertNull(mappers.garment(null))
    }

    @Test fun `mapLook antepone el mood a las etiquetas y usa contadores _count`() {
        val l: LookDto = parse(
            """{"id":"l1","title":"Oficina","mood":"chic","tags":"[\"formal\"]","isPublic":true,"createdAt":"2026-02-01",
               "userId":"u1","user":{"id":"u1","name":"Ana"},"_count":{"likes":4,"comments":2},"isLiked":true,
               "images":[{"url":"/api/uploads/l1.webp"}],
               "products":[{"id":"p1","name":"A","category":"top"},{"id":"p2","name":"B","category":"bottom"}]}""",
        )
        val look = mappers.look(l)!!
        assertEquals("Oficina", look.name)
        assertEquals(listOf("chic", "formal"), look.tags) // mood primero
        assertEquals(listOf("p1", "p2"), look.garmentIds)
        assertEquals(4, look.likesCount)
        assertEquals(2, look.commentsCount)
        assertTrue(look.isLiked)
        assertEquals("https://example.test/api/uploads/l1.webp", look.imageUrl)
    }

    @Test fun `mapLook no duplica el mood si ya esta en las etiquetas`() {
        val look = mappers.look(parse<LookDto>("""{"id":"l4","title":"X","mood":"chic","tags":["formal","chic"]}"""))!!
        assertEquals(listOf("formal", "chic"), look.tags)
    }

    @Test fun `mapLook acepta tags como array y cae a la imagen de la primera prenda`() {
        val l: LookDto = parse(
            """{"id":"l2","title":"X","tags":["a","b"],"likesCount":7,"commentsCount":1,
               "products":[{"id":"p1","category":"top","images":[{"url":"/api/uploads/p1.webp"}]}]}""",
        )
        val look = mappers.look(l)!!
        assertEquals(listOf("a", "b"), look.tags)
        assertEquals(7, look.likesCount)
        assertEquals("https://example.test/api/uploads/p1.webp", look.imageUrl)
    }

    @Test fun `mapLook tolera tags corruptos`() {
        val look = mappers.look(parse<LookDto>("""{"id":"l3","title":"X","tags":"no-es-json"}"""))!!
        assertTrue(look.tags.isEmpty())
    }

    @Test fun `mapTrip convierte items y prendas crudas`() {
        val t: TripDto = parse(
            """{"id":"t1","destination":"Roma","dateStart":"2026-05-01","dateEnd":"2026-05-05",
               "items":[{"id":"i1","label":"Pasaporte","checked":true,"isEssential":true},{"id":"i2","label":"Gafas"}],
               "garments":[{"id":"p1","name":"Abrigo","category":"outer","images":[{"url":"/api/uploads/o.webp"}]}]}""",
        )
        val trip = mappers.trip(t)
        assertEquals("Roma", trip.destination)
        assertEquals(2, trip.items.size)
        assertTrue(trip.items[0].checked && trip.items[0].isEssential)
        assertFalse(trip.items[1].checked)
        assertEquals("https://example.test/api/uploads/o.webp", trip.garments.single().imageUrl)
    }

    @Test fun `conversacion calcula otherUser a partir de los participantes`() {
        val c: ConversationDto = parse(
            """{"id":"c1","updatedAt":"2026-03-01","participants":[
                {"userId":"me","user":{"id":"me","name":"Yo"}},
                {"userId":"u2","user":{"id":"u2","name":"Luz","avatar":"/api/uploads/l.webp"}}],
               "messages":[{"id":"m1","content":"hola","createdAt":"2026-03-01","sender":{"id":"u2","name":"Luz"}}]}""",
        )
        val conv = mappers.conversation(c, currentUserId = "me")
        assertEquals("u2", conv.otherUser?.id)
        assertEquals("https://example.test/api/uploads/l.webp", conv.otherUser?.avatar)
        assertEquals("hola", conv.lastMessage?.content)
        assertEquals(2, conv.participants.size)
    }

    @Test fun `historia marca isOwn con el usuario actual`() {
        val s: StoryDto = parse("""{"id":"s1","userId":"me","type":"image","imageUrl":"/api/uploads/stories/s.webp","views":3,"user":{"id":"me","name":"Yo"}}""")
        assertTrue(mappers.story(s, "me").isOwn)
        assertFalse(mappers.story(s, "otro").isOwn)
        assertEquals("https://example.test/api/uploads/stories/s.webp", mappers.story(s, "me").imageUrl)
    }

    @Test fun `usuario parsea listas de estilo y conserva contadores previos`() {
        val previous = mappers.user(parse<UserDto>("""{"id":"u1","name":"Ana","followersCount":5,"garmentCount":9}"""))
        val u = mappers.user(
            parse<UserDto>("""{"id":"u1","name":"Ana","avatar":"/api/uploads/a.webp","styleColors":"[\"negro\",\"blanco\"]","styleStyles":"casual, chic"}"""),
            previous,
        )
        assertEquals(listOf("negro", "blanco"), u.styleColors)
        assertEquals(listOf("casual", "chic"), u.styleStyles)
        assertEquals("https://example.test/api/uploads/a.webp", u.avatar)
        assertEquals(5, u.followersCount)
        assertEquals(9, u.garmentCount)
    }

    @Test fun `perfil ajeno rellena al autor en looks y prendas`() {
        val p: PublicProfileDto = parse(
            """{"id":"u9","name":"Eva","avatar":"/api/uploads/e.webp","isFollowing":true,"hasPendingRequest":false,
               "looks":[{"id":"l1","title":"L"}],"products":[{"id":"p1","category":"top","forSale":true}]}""",
        )
        val profile = mappers.publicProfile(p)
        assertTrue(profile.isFollowing)
        assertEquals("Eva", profile.looks.single().userName)
        assertEquals("u9", profile.looks.single().userId)
        assertEquals("Eva", profile.garmentsForSale.single().userName)
    }

    @Test fun `fetchAllPages recorre los cursores hasta agotar`() = runTest {
        val calls = mutableListOf<String?>()
        val result = fetchAllPages<Int> { limit, cursor ->
            calls += cursor
            assertEquals(100, limit)
            when (cursor) {
                null -> PageDto(listOf(1, 2), "a", true)
                "a" -> PageDto(listOf(3), "b", true)
                else -> PageDto(listOf(4), null, false)
            }
        }
        assertEquals(listOf(1, 2, 3, 4), result)
        assertEquals(listOf(null, "a", "b"), calls)
    }
}
