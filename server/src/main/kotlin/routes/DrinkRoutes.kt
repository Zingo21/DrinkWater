package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.erbeenjoyers.drinkwater.api.DrinkLogRequest
import org.erbeenjoyers.drinkwater.api.GoalDto
import services.ApiException
import services.DrinkService
import services.UserService

private const val DEFAULT_HISTORY_LIMIT = 100L
private const val MAX_HISTORY_LIMIT = 500L

fun Route.drinkRoutes(drinks: DrinkService, users: UserService) {
    get("/drinks") {
        call.respond(drinks.drinks())
    }

    authenticate {
        post("/drink") {
            val request = call.receive<DrinkLogRequest>()
            call.respond(HttpStatusCode.Created, drinks.logDrink(call.userId, request))
        }

        route("/me") {
            get("/drinks") {
                val limit = call.longQuery("limit") ?: DEFAULT_HISTORY_LIMIT
                if (limit !in 1..MAX_HISTORY_LIMIT) {
                    throw ApiException(HttpStatusCode.BadRequest, "limit must be between 1 and $MAX_HISTORY_LIMIT")
                }
                call.respond(drinks.history(call.userId, call.longQuery("from"), call.longQuery("to"), limit.toInt()))
            }

            delete("/drinks/{logId}") {
                drinks.deleteLog(call.userId, call.intParameter("logId"))
                call.respond(HttpStatusCode.NoContent)
            }

            get("/stats") {
                call.respond(drinks.stats(call.userId, call.request.queryParameters["tz"]))
            }

            put("/goal") {
                val request = call.receive<GoalDto>()
                call.respond(users.setDailyGoal(call.userId, request.dailyGoalMl))
            }
        }
    }
}

private fun RoutingCall.longQuery(name: String): Long? =
    request.queryParameters[name]?.let {
        it.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "Invalid $name")
    }
