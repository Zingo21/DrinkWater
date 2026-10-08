package org.erbeenjoyers.drinkwater.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSecure
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.erbeenjoyers.drinkwater.api.AuthResponse
import org.erbeenjoyers.drinkwater.api.DEFAULT_AMOUNT_ML
import org.erbeenjoyers.drinkwater.api.DevicePlatform
import org.erbeenjoyers.drinkwater.api.DeviceRegistration
import org.erbeenjoyers.drinkwater.api.DrinkDto
import org.erbeenjoyers.drinkwater.api.DrinkLogDto
import org.erbeenjoyers.drinkwater.api.DrinkLogEntryDto
import org.erbeenjoyers.drinkwater.api.DrinkLogRequest
import org.erbeenjoyers.drinkwater.api.ErrorResponse
import org.erbeenjoyers.drinkwater.api.FriendRequestCreate
import org.erbeenjoyers.drinkwater.api.FriendRequestCreateResponse
import org.erbeenjoyers.drinkwater.api.FriendRequestsResponse
import org.erbeenjoyers.drinkwater.api.GoalDto
import org.erbeenjoyers.drinkwater.api.LeaderboardEntryDto
import org.erbeenjoyers.drinkwater.api.LoginRequest
import org.erbeenjoyers.drinkwater.api.Notification
import org.erbeenjoyers.drinkwater.api.RegisterRequest
import org.erbeenjoyers.drinkwater.api.StatsDto
import org.erbeenjoyers.drinkwater.api.UserDto

/** The server answered with an error status. [message] is the server's own description of what went wrong. */
class ApiError(val status: HttpStatusCode, override val message: String) : Exception(message)

/**
 * Client for the DrinkWater server. Calls throw [ApiError] when the server rejects the request,
 * and whatever the HTTP engine throws when the server can't be reached.
 *
 * [baseUrl] is the server root, e.g. `http://10.0.2.2:8080`. By default the HTTP engine on the
 * classpath is used (OkHttp on Android, Darwin on iOS).
 */
class DrinkWaterApi(baseUrl: String, httpClient: HttpClient = HttpClient()) {
    /** Sent as `Authorization: Bearer <token>`. Set by [register] and [login]; restore it yourself after a restart. */
    var token: String? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val notificationsUrl = URLBuilder(baseUrl).apply {
        protocol = if (protocol.isSecure()) URLProtocol.WSS else URLProtocol.WS
        appendPathSegments("notifications")
    }.buildString()

    private val client = httpClient.config {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
        install(WebSockets)
        defaultRequest {
            url(baseUrl)
        }
    }

    suspend fun register(username: String, password: String): AuthResponse =
        client.post("/auth/register") { jsonBody(RegisterRequest(username, password)) }
            .result<AuthResponse>()
            .also { token = it.token }

    suspend fun login(username: String, password: String): AuthResponse =
        client.post("/auth/login") { jsonBody(LoginRequest(username, password)) }
            .result<AuthResponse>()
            .also { token = it.token }

    suspend fun me(): UserDto = client.get("/me") { authorize() }.result()

    suspend fun drinks(): List<DrinkDto> = client.get("/drinks").result()

    /**
     * Logs a drink for the logged-in user; the server notifies their friends. [timeZone] is the
     * user's IANA time zone, which decides what counts as "today" for the daily goal.
     */
    suspend fun logDrink(drinkId: Int, amountMl: Int = DEFAULT_AMOUNT_ML, timeZone: String? = null): DrinkLogDto =
        client.post("/drink") {
            authorize()
            jsonBody(DrinkLogRequest(drinkId, amountMl, timeZone))
        }.result()

    /** The logged-in user's own logs, newest first. [from] is inclusive and [to] exclusive, in epoch milliseconds. */
    suspend fun drinkHistory(from: Long? = null, to: Long? = null, limit: Int? = null): List<DrinkLogEntryDto> =
        client.get("/me/drinks") {
            authorize()
            parameter("from", from)
            parameter("to", to)
            parameter("limit", limit)
        }.result()

    suspend fun deleteDrinkLog(logId: Int) {
        client.delete("/me/drinks/$logId") { authorize() }.result<Unit>()
    }

    /** Today's total, the streak and the last week, with days as they fall in [timeZone] (UTC if null). */
    suspend fun stats(timeZone: String? = null): StatsDto =
        client.get("/me/stats") {
            authorize()
            parameter("tz", timeZone)
        }.result()

    suspend fun setDailyGoal(dailyGoalMl: Int): GoalDto =
        client.put("/me/goal") {
            authorize()
            jsonBody(GoalDto(dailyGoalMl))
        }.result()

    suspend fun friends(): List<UserDto> = client.get("/friends") { authorize() }.result()

    /**
     * The logged-in user and their friends, whoever has drunk the most today first. Days are
     * drawn in [timeZone] for everyone (UTC if null).
     */
    suspend fun leaderboard(timeZone: String? = null): List<LeaderboardEntryDto> =
        client.get("/friends/leaderboard") {
            authorize()
            parameter("tz", timeZone)
        }.result()

    suspend fun removeFriend(friendId: Int) {
        client.delete("/friends/$friendId") { authorize() }.result<Unit>()
    }

    /** Reminds a friend to drink. Fails with 429 if you nudged them a moment ago. */
    suspend fun nudge(friendId: Int) {
        client.post("/friends/$friendId/nudge") { authorize() }.result<Unit>()
    }

    suspend fun friendRequests(): FriendRequestsResponse = client.get("/friends/requests") { authorize() }.result()

    /** Sends a friend request to [username]. If they already sent one to you, it is accepted instead. */
    suspend fun sendFriendRequest(username: String): FriendRequestCreateResponse =
        client.post("/friends/requests") {
            authorize()
            jsonBody(FriendRequestCreate(username))
        }.result()

    /** Accepts an incoming request and returns the new friend. */
    suspend fun acceptFriendRequest(requestId: Int): UserDto =
        client.post("/friends/requests/$requestId/accept") { authorize() }.result()

    /** Declines an incoming request or cancels an outgoing one. */
    suspend fun deleteFriendRequest(requestId: Int) {
        client.delete("/friends/requests/$requestId") { authorize() }.result<Unit>()
    }

    /** Makes this device get the logged-in user's push notifications. [deviceToken] comes from Firebase Cloud Messaging. */
    suspend fun registerDevice(deviceToken: String, platform: DevicePlatform) {
        client.put("/me/devices") {
            authorize()
            jsonBody(DeviceRegistration(deviceToken, platform))
        }.result<Unit>()
    }

    /**
     * Stops push notifications to this device. [authToken] lets a log-out finish this call
     * after [token] has already been cleared.
     */
    suspend fun unregisterDevice(deviceToken: String, authToken: String? = token) {
        client.delete("/me/devices/${deviceToken.encodeURLPathPart()}") {
            authToken?.let { bearerAuth(it) }
        }.result<Unit>()
    }

    /**
     * Live notifications for the logged-in user. Collecting opens a WebSocket and [onConnected] is called
     * once it is up. The flow completes when the server closes the connection and fails if the
     * connection can't be made or breaks, so collect it again to reconnect.
     */
    fun notifications(onConnected: () -> Unit = {}): Flow<Notification> = channelFlow {
        client.webSocket(notificationsUrl, request = { authorize() }) {
            onConnected()
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val notification = try {
                    json.decodeFromString(Notification.serializer(), frame.readText())
                } catch (e: SerializationException) {
                    // A kind of notification this version of the app doesn't know about yet.
                    continue
                }
                send(notification)
            }
        }
    }

    fun close() {
        client.close()
    }

    private fun HttpRequestBuilder.authorize() {
        token?.let { bearerAuth(it) }
    }

    private inline fun <reified T> HttpRequestBuilder.jsonBody(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend inline fun <reified T> HttpResponse.result(): T {
        if (status.isSuccess()) return body()
        val message = try {
            body<ErrorResponse>().error
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not one of our own error bodies, e.g. a proxy's HTML error page.
            status.description
        }
        throw ApiError(status, message)
    }
}
