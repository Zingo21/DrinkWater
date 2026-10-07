package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.erbeenjoyers.drinkwater.api.DrinkLogRequest
import services.DrinkService

fun Route.drinkRoutes(drinks: DrinkService) {
    get("/drinks") {
        call.respond(drinks.drinks())
    }

    authenticate {
        post("/drink") {
            val request = call.receive<DrinkLogRequest>()
            call.respond(HttpStatusCode.Created, drinks.logDrink(call.userId, request.drinkId))
        }
    }
}
