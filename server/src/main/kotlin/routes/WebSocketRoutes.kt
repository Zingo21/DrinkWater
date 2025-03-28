package routes

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.util.concurrent.ConcurrentHashMap

val connections = ConcurrentHashMap.newKeySet<DefaultWebSocketServerSession>()

fun Route.webSocketRoutes() {
    webSocket("/drinkUpdates") {
        connections.add(this)
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val message = frame.readText()
                    // Send message to connected clients
                    connections.forEach { session ->
                        session.send("En vän har druckit: $message")
                    }
                }
            }
        } finally {
            connections.remove(this)
        }
    }
}