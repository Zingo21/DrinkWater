package routes

import database.DrinkLogs
import database.Drinks
import database.Friends
import database.Users
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.erbeenjoyers.drinkwater.dto.DrinkDto
import org.erbeenjoyers.drinkwater.dto.DrinkLogDto
import org.erbeenjoyers.drinkwater.dto.DrinkLogRequest
import org.erbeenjoyers.drinkwater.dto.NotificationType
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import services.NotificationService

fun Route.drinkRoutes() {
    post("/drink") {
        val request = call.receive<DrinkLogRequest>()

        val (drinkName, friendIds) = transaction {
            val drink = Drinks.select { Drinks.id eq request.drinkId }.firstOrNull()
                ?: return@transaction null to emptyList()
            DrinkLogs.insert {
                it[userId] = request.userId
                it[drinkId] = request.drinkId
                it[timestamp] = System.currentTimeMillis()
            }
            // Öka användarens poäng och markera att det inte längre är dens tur.
            Users.update({ Users.id eq request.userId }) {
                with(SqlExpressionBuilder) {
                    it[score] = score + 1
                }
                it[isYourTurn] = false
            }
            val friendIds = Friends
                .select { Friends.userId eq request.userId }
                .map { it[Friends.friendId] }
            drink[Drinks.name] to friendIds
        }

        if (drinkName == null) {
            return@post call.respond(HttpStatusCode.NotFound, "Drink not found")
        }

        // Notifiera alla vänner: "X har druckit" och "det är din tur".
        val fromUser = transaction {
            Users.select { Users.id eq request.userId }.firstOrNull()?.get(Users.username)
        } ?: "Någon"
        friendIds.forEach { friendId ->
            NotificationService.createAndSend(
                fromUserId = request.userId,
                fromUsername = fromUser,
                toUserId = friendId,
                type = NotificationType.DRANK,
                message = "$fromUser har druckit $drinkName!"
            )
            NotificationService.createAndSend(
                fromUserId = request.userId,
                fromUsername = fromUser,
                toUserId = friendId,
                type = NotificationType.YOUR_TURN,
                message = "Det är din tur att dricka, $friendId!"
            )
        }

        call.respondText("Drink added!", status = HttpStatusCode.Created)
    }

    get("/drinks") {
        val drinks = transaction {
            Drinks.selectAll().map {
                DrinkDto(id = it[Drinks.id], name = it[Drinks.name])
            }
        }
        call.respond(drinks)
    }

    get("/drinks/logs/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid userId")
        val logs = transaction {
            (DrinkLogs innerJoin Drinks)
                .select { DrinkLogs.userId eq userId }
                .orderBy(DrinkLogs.timestamp, org.jetbrains.exposed.sql.SortOrder.DESC)
                .map {
                    DrinkLogDto(
                        id = it[DrinkLogs.id],
                        userId = userId,
                        drinkId = it[DrinkLogs.drinkId],
                        drinkName = it[Drinks.name],
                        timestamp = it[DrinkLogs.timestamp]
                    )
                }
        }
        call.respond(logs)
    }
}
