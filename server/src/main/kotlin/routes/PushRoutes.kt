package routes

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import services.NotificationService

fun Route.pushRoutes() {
    webSocket("/push/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
            ?: return@webSocket close()
        NotificationService.registerClient(userId, this)
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val message = frame.readText()
                    println("Received push message: $message")
                }
            }
        } finally {
            NotificationService.unregisterClient(userId)
        }
    }
}
