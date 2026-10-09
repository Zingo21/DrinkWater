package services

import database.Notifications
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.erbeenjoyers.drinkwater.dto.NotificationDto
import org.erbeenjoyers.drinkwater.dto.NotificationType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * Hanterar notifieringar till vänner. Sparar dem i databasen så att appen kan
 * hämta olästa vid uppstart, och skickar dem i realtid via WebSocket om mottagaren
 * är ansluten.
 */
object NotificationService {
    private val clients = mutableMapOf<Int, DefaultWebSocketServerSession>()
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun registerClient(userId: Int, session: DefaultWebSocketServerSession) {
        lock.withLock {
            clients[userId] = session
        }
    }

    suspend fun unregisterClient(userId: Int) {
        lock.withLock {
            clients.remove(userId)
        }
    }

    /**
     * Skapar en notifiering i databasen och skickar den i realtid om mottagaren
     * är ansluten via WebSocket. Returnerar den sparade notifieringen.
     */
    suspend fun createAndSend(
        fromUserId: Int,
        fromUsername: String,
        toUserId: Int,
        type: NotificationType,
        message: String
    ): NotificationDto {
        val notification = transaction {
            val id = Notifications.insert {
                it[Notifications.fromUserId] = fromUserId
                it[Notifications.toUserId] = toUserId
                it[Notifications.type] = type.name
                it[Notifications.message] = message
                it[Notifications.timestamp] = System.currentTimeMillis()
                it[Notifications.read] = false
            } get Notifications.id
            NotificationDto(
                id = id,
                toUserId = toUserId,
                fromUserId = fromUserId,
                fromUsername = fromUsername,
                type = type,
                message = message,
                timestamp = System.currentTimeMillis(),
                read = false
            )
        }

        // Skicka i realtid om mottagaren är ansluten.
        lock.withLock {
            clients[toUserId]?.send(json.encodeToString(NotificationDto.serializer(), notification))
        }

        return notification
    }

    fun listForUser(userId: Int): List<NotificationDto> = transaction {
        val rows = Notifications
            .select { Notifications.toUserId eq userId }
            .orderBy(Notifications.timestamp, org.jetbrains.exposed.sql.SortOrder.DESC)
            .toList()
        rows.map { row ->
            val fromUser = database.Users
                .select { database.Users.id eq row[Notifications.fromUserId] }
                .firstOrNull()
            NotificationDto(
                id = row[Notifications.id],
                toUserId = userId,
                fromUserId = row[Notifications.fromUserId],
                fromUsername = fromUser?.get(database.Users.username) ?: "Okänd",
                type = NotificationType.valueOf(row[Notifications.type]),
                message = row[Notifications.message],
                timestamp = row[Notifications.timestamp],
                read = row[Notifications.read]
            )
        }
    }

    fun markRead(notificationId: Int): Boolean = transaction {
        val updated = Notifications.update({ Notifications.id eq notificationId }) {
            it[read] = true
        }
        updated > 0
    }
}
