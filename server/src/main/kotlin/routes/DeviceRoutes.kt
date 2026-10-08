package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.erbeenjoyers.drinkwater.api.DeviceRegistration
import services.ApiException
import services.DeviceService

fun Route.deviceRoutes(devices: DeviceService) {
    authenticate {
        route("/me/devices") {
            put {
                devices.register(call.userId, call.receive<DeviceRegistration>())
                call.respond(HttpStatusCode.NoContent)
            }

            delete("/{token}") {
                val token = call.parameters["token"] ?: throw ApiException(HttpStatusCode.BadRequest, "Invalid token")
                devices.unregister(call.userId, token)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
