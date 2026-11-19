package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.erbeenjoyers.drinkwater.api.DeviceRegistration
import org.erbeenjoyers.drinkwater.api.WebPushKeyDto
import push.PushSender
import services.ApiException
import services.DeviceService

fun Route.deviceRoutes(devices: DeviceService, push: PushSender) {
    get("/push/web-key") {
        val key = push.webPushKey ?: throw ApiException(HttpStatusCode.NotFound, "Web push is not set up on this server")
        call.respond(WebPushKeyDto(key))
    }

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
