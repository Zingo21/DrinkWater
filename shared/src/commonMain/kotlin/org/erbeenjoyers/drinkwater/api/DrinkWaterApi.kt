package org.erbeenjoyers.drinkwater.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.erbeenjoyers.drinkwater.ServerConfig
import org.erbeenjoyers.drinkwater.dto.AddFriendRequest
import org.erbeenjoyers.drinkwater.dto.CreateNotificationRequest
import org.erbeenjoyers.drinkwater.dto.CreateUserRequest
import org.erbeenjoyers.drinkwater.dto.DrinkDto
import org.erbeenjoyers.drinkwater.dto.DrinkLogRequest
import org.erbeenjoyers.drinkwater.dto.FriendDto
import org.erbeenjoyers.drinkwater.dto.NotificationDto
import org.erbeenjoyers.drinkwater.dto.UserDto

/**
 * Ktor-baserad klient som pratar med DrinkWater-servern. Delas mellan
 * Android och iOS via Kotlin Multiplatform. Varje metod mappar en server-route.
 */
class DrinkWaterApi(
    private val client: HttpClient = defaultClient(),
    private val baseUrl: String = ServerConfig.baseUrl,
) {
    // --- Användare ---
    suspend fun createUser(username: String): UserDto =
        client.post("$baseUrl/users") {
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest(username))
        }.body()

    suspend fun getUser(userId: Int): UserDto =
        client.get("$baseUrl/users/$userId").body()

    // --- Drycker ---
    suspend fun getDrinks(): List<DrinkDto> =
        client.get("$baseUrl/drinks").body()

    suspend fun logDrink(userId: Int, drinkId: Int): String =
        client.post("$baseUrl/drink") {
            contentType(ContentType.Application.Json)
            setBody(DrinkLogRequest(userId, drinkId))
        }.body()

    // --- Vänner ---
    suspend fun getFriends(userId: Int): List<FriendDto> =
        client.get("$baseUrl/friends") { parameter("userId", userId) }.body()

    suspend fun addFriend(userId: Int, friendUsername: String): FriendDto =
        client.post("$baseUrl/friends") {
            contentType(ContentType.Application.Json)
            setBody(AddFriendRequest(userId, friendUsername))
        }.body()

    suspend fun removeFriend(userId: Int, friendId: Int): Boolean =
        client.delete("$baseUrl/friends") {
            parameter("userId", userId)
            parameter("friendId", friendId)
        }.status.value in 200..299

    // --- Notifieringar ---
    suspend fun getNotifications(userId: Int): List<NotificationDto> =
        client.get("$baseUrl/notifications") { parameter("userId", userId) }.body()

    suspend fun markNotificationRead(notificationId: Int): Boolean =
        client.post("$baseUrl/notifications/$notificationId/read").status.value in 200..299

    suspend fun sendNotification(
        fromUserId: Int,
        toUserId: Int,
        request: CreateNotificationRequest
    ): NotificationDto = client.post("$baseUrl/notifications") {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    companion object {
        fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
        }
    }
}
