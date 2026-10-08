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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.erbeenjoyers.drinkwater.api.AuthResponse
import org.erbeenjoyers.drinkwater.api.DEFAULT_AMOUNT_ML
import org.erbeenjoyers.drinkwater.api.DEFAULT_DAILY_GOAL_ML
import org.erbeenjoyers.drinkwater.api.DevicePlatform
import org.erbeenjoyers.drinkwater.api.DrinkDto
import org.erbeenjoyers.drinkwater.api.DrinkLogRequest
import org.erbeenjoyers.drinkwater.api.FriendRequestCreate
import org.erbeenjoyers.drinkwater.api.FriendRequestCreateResponse
import org.erbeenjoyers.drinkwater.api.FriendRequestResult
import org.erbeenjoyers.drinkwater.api.FriendRequestsResponse
import org.erbeenjoyers.drinkwater.api.LeaderboardEntryDto
import org.erbeenjoyers.drinkwater.api.LoginRequest
import org.erbeenjoyers.drinkwater.api.Notification
import org.erbeenjoyers.drinkwater.api.RegisterRequest
import org.erbeenjoyers.drinkwater.api.UserDto
import org.erbeenjoyers.drinkwater.client.ApiError
import org.erbeenjoyers.drinkwater.client.DrinkWaterApi
import org.erbeenjoyers.drinkwater.module
import push.NoPush
import push.PushMessage
import push.PushResult
import push.PushSender
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApiTest {
    /** Records what would have been pushed. Tokens in [dead] are reported as no longer registered. */
    private class RecordingPush(private val dead: Set<String> = emptySet()) : PushSender {
        val sent = Channel<Pair<String, PushMessage>>(Channel.UNLIMITED)

        override suspend fun send(deviceToken: String, message: PushMessage): PushResult {
            sent.send(deviceToken to message)
            return if (deviceToken in dead) PushResult.UNREGISTERED else PushResult.SENT
        }

        /** Everything pushed until nothing more arrives for a moment. */
        suspend fun drain(): List<Pair<String, PushMessage>> = buildList {
            while (true) add(withTimeoutOrNull(300) { sent.receive() } ?: break)
        }
    }

    private fun apiTest(push: PushSender = NoPush, block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) {
        val dbFile = File.createTempFile("drinkwater-test", ".db")
        try {
            testApplication {
                application {
                    module(AppConfig("jdbc:sqlite:${dbFile.absolutePath}", JwtConfig(secret = "test-secret")), push)
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

    @Test
    fun `the shared client can register, log in and log a drink`() = apiTest { client ->
        val api = DrinkWaterApi("http://localhost", client)

        val registered = api.register("anna", "password123")
        assertEquals(registered.user, api.me())

        val water = api.drinks().first()
        val log = api.logDrink(water.id)
        assertEquals(registered.user.id, log.userId)
        assertEquals(water.id, log.drinkId)

        val wrongPassword = assertFailsWith<ApiError> { api.login("anna", "wrong-password") }
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals("Wrong username or password", wrongPassword.message)

        api.token = null
        assertEquals(HttpStatusCode.Unauthorized, assertFailsWith<ApiError> { api.logDrink(water.id) }.status)

        assertEquals(registered.user, api.login("Anna", "password123").user)
        api.logDrink(water.id)
    }

    @Test
    fun `the shared client handles friends and live notifications`() = apiTest { client ->
        val anna = DrinkWaterApi("http://localhost", client)
        val bertil = DrinkWaterApi("http://localhost", client)
        val annaUser = anna.register("anna", "password123").user
        val bertilUser = bertil.register("bertil", "password123").user

        assertEquals(HttpStatusCode.NotFound, assertFailsWith<ApiError> { anna.sendFriendRequest("nobody") }.status)

        val sent = anna.sendFriendRequest("Bertil")
        assertEquals(FriendRequestResult.SENT, sent.result)
        assertEquals(listOf(sent.request), anna.friendRequests().outgoing)
        assertEquals(listOf(sent.request), bertil.friendRequests().incoming)

        // Declining removes it, after which a new request can be sent and accepted.
        bertil.deleteFriendRequest(sent.request.id)
        assertTrue(anna.friendRequests().outgoing.isEmpty())
        val again = anna.sendFriendRequest("bertil")
        assertEquals(annaUser, bertil.acceptFriendRequest(again.request.id))
        assertEquals(listOf(bertilUser), anna.friends())
        assertEquals(listOf(annaUser), bertil.friends())

        val water = anna.drinks().first()
        coroutineScope {
            val connected = CompletableDeferred<Unit>()
            val notification = async { bertil.notifications { connected.complete(Unit) }.first() }
            withTimeout(5_000) { connected.await() }
            anna.logDrink(water.id)

            val received = withTimeout(5_000) { notification.await() }
            assertIs<Notification.FriendDrank>(received)
            assertEquals(annaUser, received.friend)
            assertEquals(water, received.drink)
        }

        anna.removeFriend(bertilUser.id)
        assertTrue(bertil.friends().isEmpty())
    }

    @Test
    fun `amounts, history, stats and the daily goal`() = apiTest { client ->
        val anna = DrinkWaterApi("http://localhost", client)
        val bertil = DrinkWaterApi("http://localhost", client)
        val annaUser = anna.register("anna", "password123").user
        bertil.register("bertil", "password123")
        bertil.acceptFriendRequest(anna.sendFriendRequest("bertil").request.id)
        val water = anna.drinks().first()
        val zone = "Europe/Stockholm"

        val empty = anna.stats(zone)
        assertEquals(DEFAULT_DAILY_GOAL_ML, empty.goalMl)
        assertEquals(0, empty.todayMl)
        assertEquals(0, empty.streakDays)
        assertEquals(7, empty.days.size)

        assertEquals(HttpStatusCode.BadRequest, assertFailsWith<ApiError> { anna.setDailyGoal(100) }.status)
        assertEquals(800, anna.setDailyGoal(800).dailyGoalMl)

        assertEquals(HttpStatusCode.BadRequest, assertFailsWith<ApiError> { anna.logDrink(water.id, 0) }.status)
        assertEquals(HttpStatusCode.BadRequest, assertFailsWith<ApiError> { anna.logDrink(water.id, 250, "Nowhere/City") }.status)
        assertEquals(HttpStatusCode.BadRequest, assertFailsWith<ApiError> { anna.stats("Nowhere/City") }.status)

        coroutineScope {
            val connected = CompletableDeferred<Unit>()
            val reachedGoal = async {
                bertil.notifications { connected.complete(Unit) }.first { it is Notification.FriendReachedGoal }
            }
            withTimeout(5_000) { connected.await() }

            val first = anna.logDrink(water.id, 500, zone)
            assertEquals(500, first.amountMl)
            assertEquals(false, first.goalReached)
            // Only the drink that crosses the goal reports it.
            assertEquals(true, anna.logDrink(water.id, 330, zone).goalReached)
            assertEquals(false, anna.logDrink(water.id, timeZone = zone).goalReached)

            val notification = withTimeout(5_000) { reachedGoal.await() }
            assertEquals(Notification.FriendReachedGoal(annaUser, 800), notification)
        }

        val stats = anna.stats(zone)
        assertEquals(800, stats.goalMl)
        assertEquals(500 + 330 + DEFAULT_AMOUNT_ML, stats.todayMl)
        assertEquals(1, stats.streakDays)
        assertEquals(stats.todayMl, stats.days.last().totalMl)
        assertTrue(stats.days.dropLast(1).all { it.totalMl == 0 })

        val history = anna.drinkHistory()
        assertEquals(listOf(DEFAULT_AMOUNT_ML, 330, 500), history.map { it.amountMl })
        assertTrue(history.all { it.drink == water })
        assertEquals(1, anna.drinkHistory(limit = 1).size)
        assertTrue(anna.drinkHistory(to = history.last().timestamp).isEmpty())
        assertEquals(3, anna.drinkHistory(from = history.last().timestamp).size)
        assertTrue(bertil.drinkHistory().isEmpty())

        // You can only delete your own logs.
        assertEquals(HttpStatusCode.NotFound, assertFailsWith<ApiError> { bertil.deleteDrinkLog(history.first().id) }.status)
        anna.deleteDrinkLog(history.first().id)
        assertEquals(830, anna.stats(zone).todayMl)
    }

    @Test
    fun `the leaderboard ranks you and your friends by what you drank today`() = apiTest { client ->
        val anna = DrinkWaterApi("http://localhost", client)
        val bertil = DrinkWaterApi("http://localhost", client)
        val cecilia = DrinkWaterApi("http://localhost", client)
        val david = DrinkWaterApi("http://localhost", client)
        val annaUser = anna.register("anna", "password123").user
        val bertilUser = bertil.register("bertil", "password123").user
        val ceciliaUser = cecilia.register("cecilia", "password123").user
        val davidUser = david.register("david", "password123").user
        bertil.acceptFriendRequest(anna.sendFriendRequest("bertil").request.id)
        cecilia.acceptFriendRequest(anna.sendFriendRequest("cecilia").request.id)
        // A request that hasn't been accepted doesn't put anyone on the board.
        anna.sendFriendRequest("david")
        val water = anna.drinks().first()
        val zone = "Europe/Stockholm"

        assertEquals(listOf(LeaderboardEntryDto(davidUser, 0, DEFAULT_DAILY_GOAL_ML, 0)), david.leaderboard(zone))

        bertil.setDailyGoal(500)
        bertil.logDrink(water.id, 600, zone)
        anna.logDrink(water.id, 400, zone)
        anna.logDrink(water.id, 500, zone)
        david.logDrink(water.id, 5_000, zone)

        assertEquals(
            listOf(
                LeaderboardEntryDto(annaUser, 900, DEFAULT_DAILY_GOAL_ML, 0),
                LeaderboardEntryDto(bertilUser, 600, 500, 1),
                LeaderboardEntryDto(ceciliaUser, 0, DEFAULT_DAILY_GOAL_ML, 0),
            ),
            anna.leaderboard(zone),
        )
        // Bertil and Cecilia aren't friends with each other.
        assertEquals(listOf(annaUser, bertilUser), bertil.leaderboard(zone).map { it.user })
        assertEquals(HttpStatusCode.BadRequest, assertFailsWith<ApiError> { anna.leaderboard("Nowhere/City") }.status)
    }

    @Test
    fun `nudging a friend`() = apiTest { client ->
        val anna = DrinkWaterApi("http://localhost", client)
        val bertil = DrinkWaterApi("http://localhost", client)
        val cecilia = DrinkWaterApi("http://localhost", client)
        val annaUser = anna.register("anna", "password123").user
        val bertilUser = bertil.register("bertil", "password123").user
        val ceciliaUser = cecilia.register("cecilia", "password123").user
        bertil.acceptFriendRequest(anna.sendFriendRequest("bertil").request.id)

        // Only friends can be nudged.
        assertEquals(HttpStatusCode.NotFound, assertFailsWith<ApiError> { anna.nudge(ceciliaUser.id) }.status)
        assertEquals(HttpStatusCode.NotFound, assertFailsWith<ApiError> { anna.nudge(annaUser.id) }.status)

        coroutineScope {
            val connected = CompletableDeferred<Unit>()
            val notification = async { bertil.notifications { connected.complete(Unit) }.first() }
            withTimeout(5_000) { connected.await() }
            anna.nudge(bertilUser.id)

            assertEquals(Notification.Nudge(annaUser), withTimeout(5_000) { notification.await() })
        }

        // Not again right away, but the cooldown is per direction.
        assertEquals(HttpStatusCode.TooManyRequests, assertFailsWith<ApiError> { anna.nudge(bertilUser.id) }.status)
        bertil.nudge(annaUser.id)
    }

    @Test
    fun `users without the app open get a push instead`() {
        val push = RecordingPush(dead = setOf("old-phone"))
        apiTest(push) { client ->
            val anna = DrinkWaterApi("http://localhost", client)
            val bertil = DrinkWaterApi("http://localhost", client)
            anna.register("anna", "password123")
            val bertilUser = bertil.register("bertil", "password123").user
            bertil.acceptFriendRequest(anna.sendFriendRequest("bertil").request.id)
            // Anna has no registered device, so accepting her request pushed nothing.
            assertTrue(push.drain().isEmpty())

            bertil.registerDevice("phone", DevicePlatform.ANDROID)
            bertil.registerDevice("phone", DevicePlatform.ANDROID)
            bertil.registerDevice("old-phone", DevicePlatform.IOS)
            assertEquals(HttpStatusCode.BadRequest, assertFailsWith<ApiError> { bertil.registerDevice(" ", DevicePlatform.IOS) }.status)

            val water = anna.drinks().first()
            anna.logDrink(water.id, 330)
            val pushed = push.drain()
            assertEquals(setOf("phone", "old-phone"), pushed.map { it.first }.toSet())
            assertEquals(2, pushed.size)
            assertTrue(pushed.all { it.second.body == "anna drank 330 ml ${water.name}" })

            // The token FCM reported as gone has been forgotten.
            anna.nudge(bertilUser.id)
            assertEquals(listOf("phone" to "anna nudged you: time for a drink of water"), push.drain().map { it.first to it.second.body })

            // With the app open the notification arrives live and nothing is pushed.
            coroutineScope {
                val connected = CompletableDeferred<Unit>()
                val live = async { bertil.notifications { connected.complete(Unit) }.first() }
                withTimeout(5_000) { connected.await() }
                anna.logDrink(water.id)
                assertIs<Notification.FriendDrank>(withTimeout(5_000) { live.await() })
            }
            assertTrue(push.drain().isEmpty())

            // A device that logs in as someone else stops getting the previous user's pushes.
            anna.registerDevice("phone", DevicePlatform.ANDROID)
            anna.logDrink(water.id)
            assertTrue(push.drain().isEmpty())

            // And unregistering, as on log-out, stops them altogether.
            bertil.nudge(anna.me().id)
            assertEquals(listOf("phone"), push.drain().map { it.first })
            anna.unregisterDevice("phone")
            bertil.logDrink(water.id)
            assertTrue(push.drain().isEmpty())
        }
    }
}
