package routes

import database.Users
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.erbeenjoyers.drinkwater.dto.CreateUserRequest
import org.erbeenjoyers.drinkwater.dto.UserDto
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.userRoutes() {
    post("/users") {
        val request = call.receive<CreateUserRequest>()
        val username = request.username.trim()
        require(username.isNotBlank()) { "Username must not be blank" }

        val user = transaction {
            val existing = Users.select { Users.username eq username }.firstOrNull()
            if (existing != null) {
                UserDto(
                    id = existing[Users.id],
                    username = existing[Users.username],
                    score = existing[Users.score],
                    isYourTurn = existing[Users.isYourTurn]
                )
            } else {
                val id = Users.insert {
                    it[Users.username] = username
                } get Users.id
                UserDto(id = id, username = username, score = 0, isYourTurn = false)
            }
        }
        call.respond(user)
    }

    get("/users/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid id")
        val user = transaction {
            Users.select { Users.id eq id }.firstOrNull()
        } ?: return@get call.respond(HttpStatusCode.NotFound, "User not found")
        call.respond(
            UserDto(
                id = user[Users.id],
                username = user[Users.username],
                score = user[Users.score],
                isYourTurn = user[Users.isYourTurn]
            )
        )
    }
}
