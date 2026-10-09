package routes

import database.Friends
import database.Users
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.erbeenjoyers.drinkwater.dto.AddFriendRequest
import org.erbeenjoyers.drinkwater.dto.FriendDto
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.friendRoutes() {
    get("/friends") {
        val requestedUserId = call.parameters["userId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing userId")
        val friends = transaction {
            val rows = Friends.select { Friends.userId eq requestedUserId }.toList()
            rows.map { row ->
                val friendId = row[Friends.friendId]
                val friend = Users.select { Users.id eq friendId }.first()
                FriendDto(
                    id = row[Friends.id],
                    userId = requestedUserId,
                    friendId = friendId,
                    friendUsername = friend[Users.username],
                    friendScore = friend[Users.score],
                    isFriendTurn = friend[Users.isYourTurn]
                )
            }
        }
        call.respond(friends)
    }

    post("/friends") {
        val request = call.receive<AddFriendRequest>()

        val friendDto = transaction {
            val friend = Users.select { Users.username eq request.friendUsername.trim() }
                .firstOrNull()
                ?: return@transaction null

            val friendId = friend[Users.id]

            // Kan inte lägga till sig själv som vän.
            if (request.userId == friendId) return@transaction null

            // Skapa relationen i båda riktningar så att båda ser varandra.
            Friends.insertIgnore {
                it[Friends.userId] = request.userId
                it[Friends.friendId] = friendId
            }
            Friends.insertIgnore {
                it[Friends.userId] = friendId
                it[Friends.friendId] = request.userId
            }

            FriendDto(
                id = Friends.select {
                    (Friends.userId eq request.userId) and (Friends.friendId eq friendId)
                }.first()[Friends.id],
                userId = request.userId,
                friendId = friendId,
                friendUsername = friend[Users.username],
                friendScore = friend[Users.score],
                isFriendTurn = friend[Users.isYourTurn]
            )
        }

        if (friendDto == null) {
            return@post call.respond(HttpStatusCode.NotFound, "User not found")
        }
        call.respond(friendDto)
    }

    delete("/friends") {
        val requestedUserId = call.parameters["userId"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing userId")
        val requestedFriendId = call.parameters["friendId"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing friendId")

        transaction {
            Friends.deleteWhere { Op.build { (Friends.userId eq requestedUserId) and (Friends.friendId eq requestedFriendId) } }
            Friends.deleteWhere { Op.build { (Friends.userId eq requestedFriendId) and (Friends.friendId eq requestedUserId) } }
        }
        call.respondText("Friend removed")
    }
}
