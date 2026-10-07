package routes

import auth.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.erbeenjoyers.drinkwater.api.AuthResponse
import org.erbeenjoyers.drinkwater.api.LoginRequest
import org.erbeenjoyers.drinkwater.api.RegisterRequest
import services.UserService

fun Route.authRoutes(users: UserService, jwt: JwtService) {
    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val user = users.register(request.username, request.password)
            call.respond(HttpStatusCode.Created, AuthResponse(jwt.createToken(user.id), user))
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val user = users.authenticate(request.username, request.password)
            call.respond(AuthResponse(jwt.createToken(user.id), user))
        }
    }

    authenticate {
        get("/me") {
            val user = users.findById(call.userId) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(user)
        }
    }
}
