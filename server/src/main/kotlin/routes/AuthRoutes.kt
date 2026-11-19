package routes

import auth.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.erbeenjoyers.drinkwater.api.AuthResponse
import org.erbeenjoyers.drinkwater.api.ChangePasswordRequest
import org.erbeenjoyers.drinkwater.api.DeleteAccountRequest
import org.erbeenjoyers.drinkwater.api.LoginRequest
import org.erbeenjoyers.drinkwater.api.RegisterRequest
import services.NotificationService
import services.UserService

/** Shared by the routes that check a password, see `install(RateLimit)` in Application.kt. */
val PASSWORD_RATE_LIMIT = RateLimitName("password")

fun Route.authRoutes(users: UserService, jwt: JwtService, notifications: NotificationService) {
    suspend fun token(userId: Int) = jwt.createToken(userId, users.tokenVersion(userId) ?: 0)

    rateLimit(PASSWORD_RATE_LIMIT) {
        route("/auth") {
            post("/register") {
                val request = call.receive<RegisterRequest>()
                val user = users.register(request.username, request.password)
                call.respond(HttpStatusCode.Created, AuthResponse(token(user.id), user))
            }

            post("/login") {
                val request = call.receive<LoginRequest>()
                val user = users.authenticate(request.username, request.password)
                call.respond(AuthResponse(token(user.id), user))
            }
        }
    }

    authenticate {
        get("/me") {
            val user = users.findById(call.userId) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(user)
        }

        rateLimit(PASSWORD_RATE_LIMIT) {
            put("/me/password") {
                val request = call.receive<ChangePasswordRequest>()
                users.changePassword(call.userId, request.currentPassword, request.newPassword)
                val user = users.findById(call.userId) ?: return@put call.respond(HttpStatusCode.NotFound)
                // The old token stopped working with the old password, so this device gets a new one.
                call.respond(AuthResponse(token(user.id), user))
            }

            delete("/me") {
                val request = call.receive<DeleteAccountRequest>()
                users.deleteAccount(call.userId, request.password)
                notifications.disconnect(call.userId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
