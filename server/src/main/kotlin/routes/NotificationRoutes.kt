package routes

import auth.UserPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import org.erbeenjoyers.drinkwater.api.NOTIFICATIONS_PROTOCOL
import services.NotificationService

/**
 * Live notifications while the app is open. The client sends its token as `Authorization: Bearer <token>`,
 * or, from a browser, as the subprotocols `drinkwater, <token>` (see [NOTIFICATIONS_PROTOCOL]), and
 * receives [org.erbeenjoyers.drinkwater.api.Notification]s as JSON text frames.
 */
fun Route.notificationRoutes(notifications: NotificationService) {
    authenticate {
        // A client that offers the subprotocol must get it back, or the browser drops the connection.
        webSocket("/notifications", protocol = NOTIFICATIONS_PROTOCOL) { listen(notifications) }
        webSocket("/notifications") { listen(notifications) }
    }
}

private suspend fun DefaultWebSocketServerSession.listen(notifications: NotificationService) {
    val userId = call.principal<UserPrincipal>()!!.userId
    notifications.register(userId, this)
    try {
        // Keep the connection open until the client disconnects; incoming frames are ignored.
        for (frame in incoming) Unit
    } finally {
        notifications.unregister(userId, this)
    }
}
