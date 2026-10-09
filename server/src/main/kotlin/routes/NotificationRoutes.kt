package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.erbeenjoyers.drinkwater.dto.CreateNotificationRequest
import org.erbeenjoyers.drinkwater.dto.NotificationDto
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import services.NotificationService

fun Route.notificationRoutes() {
    get("/notifications") {
        val userId = call.parameters["userId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing userId")
        val notifications: List<NotificationDto> = NotificationService.listForUser(userId)
        call.respond(notifications)
    }

    post("/notifications") {
        val request = call.receive<CreateNotificationRequest>()
        val fromUsername = transaction {
            // Hämta avsändarens användarnamn för notifieringen.
            database.Users.select { database.Users.id eq request.fromUserId }
                .firstOrNull()?.get(database.Users.username)
        } ?: "Någon"
        val notification = NotificationService.createAndSend(
            fromUserId = request.fromUserId,
            fromUsername = fromUsername,
            toUserId = request.toUserId,
            type = request.type,
            message = request.message
        )
        call.respond(notification)
    }

    post("/notifications/{id}/read") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val success = NotificationService.markRead(id)
        if (success) {
            call.respondText("Marked as read")
        } else {
            call.respond(HttpStatusCode.NotFound, "Notification not found")
        }
    }
}
