package routes

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import services.PushNotificationService

fun Route.pushRoutes() {
    webSocket("/push/{userId}") {
        val userId = call.parameters["userId"] ?: return@webSocket close()
        PushNotificationService.registerClient(userId, this)

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val message = frame.readText()
                    println("Received push message: $message")
                }
            }
        } finally {
            close()
        }
    }
}