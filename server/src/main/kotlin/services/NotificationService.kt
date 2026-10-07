package services

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import org.erbeenjoyers.drinkwater.api.Notification
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps track of each user's open `/notifications` WebSockets. A user can be
 * connected from several devices at once, so each user maps to a set of sessions.
 */
class NotificationService(private val json: Json) {
    private val sessions = ConcurrentHashMap<Int, MutableSet<WebSocketSession>>()

    fun register(userId: Int, session: WebSocketSession) {
        sessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    fun unregister(userId: Int, session: WebSocketSession) {
        sessions.computeIfPresent(userId) { _, userSessions ->
            userSessions.remove(session)
            userSessions.ifEmpty { null }
        }
    }

    suspend fun send(userId: Int, notification: Notification) {
        val message = json.encodeToString(Notification.serializer(), notification)
        sessions[userId]?.forEach { session ->
            try {
                session.send(message)
            } catch (e: Exception) {
                // The connection is gone; the route's finally block may not have run yet.
                unregister(userId, session)
            }
        }
    }

    suspend fun sendToAll(userIds: Collection<Int>, notification: Notification) {
        userIds.forEach { send(it, notification) }
    }
}
