package routes

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import database.DrinkLogs
import database.Users
import database.Drinks
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.drinkRoutes() {
    post("/drink") {
        val request = call.receive<DrinkLogRequest>()
        transaction {
            DrinkLogs.insert {
                it[userId] = request.userId
                it[drinkId] = request.drinkId
                it[timestamp] = System.currentTimeMillis()
            }
        }
        call.respondText("Drink added!", status = HttpStatusCode.Created)
    }

    get("/drinks") {
        val drinks = transaction {
            Drinks.selectAll().map { it[Drinks.name] }
        }
        call.respond(drinks)
    }
}

@Serializable
data class DrinkLogRequest(val userId: Int, val drinkId: Int)