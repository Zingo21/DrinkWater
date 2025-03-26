package services

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object PushNotificationService {

    private val clients = mutableMapOf<String, DefaultWebSocketServerSession>()
    private val lock = Mutex()

    suspend fun registerClient(userId: String, session: DefaultWebSocketServerSession) {
        lock.withLock {
            clients[userId] = session
        }
    }

    suspend fun sendPushNotification(userId: String, title: String, message: String) {
        lock.withLock {
            clients[userId]?.send("NOTIFICATION|$title|$message")
        }
    }
}