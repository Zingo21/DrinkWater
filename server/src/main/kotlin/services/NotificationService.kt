package services

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.erbeenjoyers.drinkwater.api.Notification
import org.erbeenjoyers.drinkwater.api.describe
import org.slf4j.LoggerFactory
import push.PushMessage
import push.PushResult
import push.PushSender
import java.util.concurrent.ConcurrentHashMap

/**
 * Delivers notifications to users. Someone with the app open gets them over their `/notifications`
 * WebSockets; a user can be connected from several devices at once, so each user maps to a set of
 * sessions. Someone without an open connection gets a push to their registered devices instead.
 *
 * Pushes are sent from [pushScope] so a slow push service never holds up the request that caused them.
 */
class NotificationService(
    private val json: Json,
    private val devices: DeviceService,
    private val push: PushSender,
    private val pushScope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger(NotificationService::class.java)
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
        var delivered = false
        sessions[userId]?.forEach { session ->
            try {
                session.send(message)
                delivered = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The connection is gone; the route's finally block may not have run yet.
                unregister(userId, session)
            }
        }
        if (!delivered && push.enabled) {
            pushScope.launch { pushTo(userId, notification) }
        }
    }

    suspend fun sendToAll(userIds: Collection<Int>, notification: Notification) {
        userIds.forEach { send(it, notification) }
    }

    private suspend fun pushTo(userId: Int, notification: Notification) {
        try {
            val message = PushMessage(title = "DrinkWater", body = notification.describe())
            devices.tokens(userId).forEach { token ->
                if (push.send(token, message) == PushResult.UNREGISTERED) devices.forget(token)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not push to user {}", userId, e)
        }
    }
}
