package routes

import auth.UserPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import services.NotificationService

/**
 * Live notifications while the app is open. The client connects with an
 * `Authorization: Bearer <token>` header and receives [org.erbeenjoyers.drinkwater.api.Notification]s as JSON text frames.
 */
fun Route.notificationRoutes(notifications: NotificationService) {
    authenticate {
        webSocket("/notifications") {
            val userId = call.principal<UserPrincipal>()!!.userId
            notifications.register(userId, this)
            try {
                // Keep the connection open until the client disconnects; incoming frames are ignored.
                for (frame in incoming) Unit
            } finally {
                notifications.unregister(userId, this)
            }
        }
    }
}
