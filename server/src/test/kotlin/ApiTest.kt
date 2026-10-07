import config.AppConfig
import config.JwtConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.erbeenjoyers.drinkwater.api.AuthResponse
import org.erbeenjoyers.drinkwater.api.DrinkDto
import org.erbeenjoyers.drinkwater.api.DrinkLogRequest
import org.erbeenjoyers.drinkwater.api.FriendRequestCreate
import org.erbeenjoyers.drinkwater.api.FriendRequestCreateResponse
import org.erbeenjoyers.drinkwater.api.FriendRequestResult
import org.erbeenjoyers.drinkwater.api.FriendRequestsResponse
import org.erbeenjoyers.drinkwater.api.LoginRequest
import org.erbeenjoyers.drinkwater.api.Notification
import org.erbeenjoyers.drinkwater.api.RegisterRequest
import org.erbeenjoyers.drinkwater.api.UserDto
import org.erbeenjoyers.drinkwater.module
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApiTest {
    private fun apiTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) {
        val dbFile = File.createTempFile("drinkwater-test", ".db")
        try {
            testApplication {
                application {
                    module(AppConfig("jdbc:sqlite:${dbFile.absolutePath}", JwtConfig(secret = "test-secret")))
                }
                val client = createClient {
                    install(ContentNegotiation) { json() }
                    install(WebSockets)
                }
                block(client)
            }
        } finally {
            dbFile.delete()
        }
    }

    private suspend fun HttpClient.register(username: String, password: String = "password123"): AuthResponse {
        val response = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username, password))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    private suspend fun HttpClient.sendFriendRequest(token: String, username: String): HttpResponse =
        post("/friends/requests") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(FriendRequestCreate(username))
        }

    @Test
    fun `register, login and fetch me`() = apiTest { client ->
        val registered = client.register("Anna")
        assertEquals("anna", registered.user.username)

        val duplicate = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("ANNA", "password123"))
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)

        val wrongPassword = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("anna", "wrong-password"))
        }
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)

        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("Anna", "password123"))
        }.body<AuthResponse>()

        val me = client.get("/me") { bearerAuth(login.token) }.body<UserDto>()
        assertEquals(registered.user, me)
    }

    @Test
    fun `rejects invalid registration and missing token`() = apiTest { client ->
        val shortPassword = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("bertil", "short"))
        }
        assertEquals(HttpStatusCode.BadRequest, shortPassword.status)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/me").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth("not-a-token") }.status)
    }

    @Test
    fun `friend request flow`() = apiTest { client ->
        val anna = client.register("anna")
        val bertil = client.register("bertil")
        val cecilia = client.register("cecilia")

        val sent = client.sendFriendRequest(anna.token, "bertil")
        assertEquals(HttpStatusCode.Created, sent.status)
        val request = sent.body<FriendRequestCreateResponse>().request

        assertEquals(HttpStatusCode.Conflict, client.sendFriendRequest(anna.token, "bertil").status)
        assertEquals(HttpStatusCode.BadRequest, client.sendFriendRequest(anna.token, "anna").status)
        assertEquals(HttpStatusCode.NotFound, client.sendFriendRequest(anna.token, "nobody").status)

        val bertilRequests = client.get("/friends/requests") { bearerAuth(bertil.token) }.body<FriendRequestsResponse>()
        assertEquals(listOf(request), bertilRequests.incoming)
        assertTrue(bertilRequests.outgoing.isEmpty())

        // Only the recipient can accept.
        assertEquals(
            HttpStatusCode.NotFound,
            client.post("/friends/requests/${request.id}/accept") { bearerAuth(anna.token) }.status,
        )
        val accepted = client.post("/friends/requests/${request.id}/accept") { bearerAuth(bertil.token) }
        assertEquals(anna.user, accepted.body<UserDto>())

        assertEquals(listOf(bertil.user), client.get("/friends") { bearerAuth(anna.token) }.body<List<UserDto>>())
        assertEquals(listOf(anna.user), client.get("/friends") { bearerAuth(bertil.token) }.body<List<UserDto>>())

        // A request to someone who already sent you one accepts it.
        client.sendFriendRequest(cecilia.token, "anna")
        val mutual = client.sendFriendRequest(anna.token, "cecilia").body<FriendRequestCreateResponse>()
        assertEquals(FriendRequestResult.ACCEPTED, mutual.result)

        val removed = client.delete("/friends/${bertil.user.id}") { bearerAuth(anna.token) }
        assertEquals(HttpStatusCode.NoContent, removed.status)
        assertEquals(listOf(cecilia.user), client.get("/friends") { bearerAuth(anna.token) }.body<List<UserDto>>())
    }

    @Test
    fun `declining a friend request removes it`() = apiTest { client ->
        val anna = client.register("anna")
        val bertil = client.register("bertil")
        val request = client.sendFriendRequest(anna.token, "bertil").body<FriendRequestCreateResponse>().request

        val declined = client.delete("/friends/requests/${request.id}") { bearerAuth(bertil.token) }
        assertEquals(HttpStatusCode.NoContent, declined.status)

        val annaRequests = client.get("/friends/requests") { bearerAuth(anna.token) }.body<FriendRequestsResponse>()
        assertTrue(annaRequests.outgoing.isEmpty())
        assertTrue(client.get("/friends") { bearerAuth(anna.token) }.body<List<UserDto>>().isEmpty())
    }

    @Test
    fun `friends are notified when someone drinks`() = apiTest { client ->
        val anna = client.register("anna")
        val bertil = client.register("bertil")
        val request = client.sendFriendRequest(anna.token, "bertil").body<FriendRequestCreateResponse>().request
        client.post("/friends/requests/${request.id}/accept") { bearerAuth(bertil.token) }

        val water = client.get("/drinks").body<List<DrinkDto>>().first()

        client.webSocket("/notifications", request = { header(HttpHeaders.Authorization, "Bearer ${bertil.token}") }) {
            val logged = client.post("/drink") {
                bearerAuth(anna.token)
                contentType(ContentType.Application.Json)
                setBody(DrinkLogRequest(water.id))
            }
            assertEquals(HttpStatusCode.Created, logged.status)

            val frame = withTimeout(5_000) { incoming.receive() }
            assertIs<Frame.Text>(frame)
            val notification = Json.decodeFromString(Notification.serializer(), frame.readText())
            assertIs<Notification.FriendDrank>(notification)
            assertEquals(anna.user, notification.friend)
            assertEquals(water, notification.drink)
        }
    }

    @Test
    fun `logging a drink requires a token`() = apiTest { client ->
        val response = client.post("/drink") {
            contentType(ContentType.Application.Json)
            setBody(DrinkLogRequest(1))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
