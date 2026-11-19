import admin.adminModule
import config.AdminConfig
import database.DatabaseFactory
import database.DrinkLogs
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import push.NoPush
import services.AdminService
import services.DeviceService
import services.NotificationService
import services.UserService
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminPanelTest {
    private val dbFile = File.createTempFile("drinkwater-admin", ".db")
    private val db = DatabaseFactory.init("jdbc:sqlite:${dbFile.absolutePath}")
    private val users = UserService(db)
    private val scope = CoroutineScope(Dispatchers.Default)
    private val notifications = NotificationService(Json, DeviceService(db), NoPush, scope)

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        dbFile.delete()
    }

    private fun panelTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application {
            adminModule(AdminConfig(8090, setOf(ADMIN)), AdminService(db), users, notifications)
        }
        block(createClient { followRedirects = false })
    }

    /** What `tailscale serve` passes on for [login]; [fromPanel] adds what a browser sends when a form is posted from the panel. */
    private suspend fun HttpClient.asAdmin(path: String, post: Boolean = false, login: String = ADMIN, fromPanel: Boolean = true): HttpResponse {
        val build: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            header("Tailscale-User-Login", login)
            if (fromPanel) header("Sec-Fetch-Site", "same-origin")
        }
        return if (post) post(path, build) else get(path, build)
    }

    @Test
    fun `only allowed people in the tailnet get in`() = panelTest { client ->
        val noIdentity = client.get("/")
        assertEquals(HttpStatusCode.Forbidden, noIdentity.status)
        assertTrue("through Tailscale" in noIdentity.bodyAsText())

        val funnel = client.get("/") {
            header("Tailscale-User-Login", ADMIN)
            header("Tailscale-Funnel-Request", "?1")
        }
        assertEquals(HttpStatusCode.Forbidden, funnel.status)

        val stranger = client.asAdmin("/", login = "someone@example.com")
        assertEquals(HttpStatusCode.Forbidden, stranger.status)
        assertTrue("someone@example.com may not use the admin panel" in stranger.bodyAsText())

        val admin = client.asAdmin("/")
        assertEquals(HttpStatusCode.OK, admin.status)
        assertEquals("DENY", admin.headers["X-Frame-Options"])
        assertEquals("no-store", admin.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `lists users with their activity`() = panelTest { client ->
        val anna = users.register("anna", "password123")
        users.register("bertil", "password123")
        db.query {
            DrinkLogs.insert {
                it[userId] = anna.id
                it[drinkId] = 1
                it[timestamp] = System.currentTimeMillis()
                it[amountMl] = 330
            }
        }

        val page = client.asAdmin("/").bodyAsText()
        assertTrue("anna" in page && "bertil" in page)
        assertTrue("never" in page, "bertil has not logged a drink")
        assertTrue(Regex("""<span class="value">330</span>\s*<span class="label">ml last 24 hours""").containsMatchIn(page))
        assertTrue("Signed in through Tailscale as $ADMIN" in page)
    }

    @Test
    fun `resets a password only from the panel's own form`() = panelTest { client ->
        val anna = users.register("anna", "password123")
        assertEquals(HttpStatusCode.OK, client.asAdmin("/users/${anna.id}/reset-password").status)

        val crossSite = client.asAdmin("/users/${anna.id}/reset-password", post = true, fromPanel = false)
        assertEquals(HttpStatusCode.Forbidden, crossSite.status)
        assertEquals(anna, users.authenticate("anna", "password123"))
        val versionBefore = users.tokenVersion(anna.id)!!

        val reset = client.asAdmin("/users/${anna.id}/reset-password", post = true)
        assertEquals(HttpStatusCode.OK, reset.status)
        val password = Regex("""<code class="password">([a-z0-9-]+)</code>""").find(reset.bodyAsText())?.groupValues?.get(1)
        assertNotNull(password)
        assertEquals(anna, users.authenticate("anna", password))
        assertEquals(versionBefore + 1, users.tokenVersion(anna.id))
    }

    @Test
    fun `deletes a user only from the panel's own form`() = panelTest { client ->
        val anna = users.register("anna", "password123")
        assertEquals(HttpStatusCode.OK, client.asAdmin("/users/${anna.id}/delete").status)

        val crossSite = client.post("/users/${anna.id}/delete") {
            header("Tailscale-User-Login", ADMIN)
            header("Sec-Fetch-Site", "cross-site")
        }
        assertEquals(HttpStatusCode.Forbidden, crossSite.status)
        assertNotNull(users.findById(anna.id))

        val deleted = client.asAdmin("/users/${anna.id}/delete", post = true)
        assertEquals(HttpStatusCode.Found, deleted.status)
        assertEquals("/?deleted=anna", deleted.headers[HttpHeaders.Location])
        assertNull(users.findById(anna.id))
        assertTrue("Deleted anna." in client.asAdmin("/?deleted=anna").bodyAsText())

        assertEquals(HttpStatusCode.NotFound, client.asAdmin("/users/${anna.id}/delete").status)
        assertFalse("anna</strong>" in client.asAdmin("/").bodyAsText())
    }

    private companion object {
        const val ADMIN = "admin@example.com"
    }
}
