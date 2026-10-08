package org.erbeenjoyers.drinkwater

import auth.JwtService
import auth.UserPrincipal
import config.AppConfig
import database.DatabaseFactory
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import kotlinx.serialization.json.Json
import org.erbeenjoyers.drinkwater.api.ErrorResponse
import push.PushSender
import routes.authRoutes
import routes.deviceRoutes
import routes.drinkRoutes
import routes.friendRoutes
import routes.notificationRoutes
import services.ApiException
import services.DeviceService
import services.DrinkService
import services.FriendService
import services.NotificationService
import services.UserService
import kotlin.time.Duration.Companion.seconds

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0") {
        module(AppConfig.fromEnvironment(developmentMode))
    }.start(wait = true)
}

fun Application.module(config: AppConfig, push: PushSender = PushSender.fromConfig(config)) {
    val json = Json { ignoreUnknownKeys = true }

    install(WebSockets) {
        pingPeriod = 30.seconds
    }

    install(ContentNegotiation) {
        json(json)
    }

    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.message))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
        }
    }

    val db = DatabaseFactory.init(config.databaseUrl)
    val jwtService = JwtService(config.jwt)
    val deviceService = DeviceService(db)
    // The application is the scope, so pushes still on their way are cancelled when the server stops.
    val notificationService = NotificationService(json, deviceService, push, pushScope = this)
    val userService = UserService(db)
    val friendService = FriendService(db, userService, notificationService)
    val drinkService = DrinkService(db, userService, friendService, notificationService)

    install(Authentication) {
        jwt {
            realm = config.jwt.realm
            verifier(jwtService.verifier)
            validate { credential ->
                credential.payload.subject?.toIntOrNull()
                    ?.takeIf { userService.findById(it) != null }
                    ?.let { UserPrincipal(it) }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing or invalid token"))
            }
        }
    }

    routing {
        get("/") {
            call.respondText("Drink Water API is running 🚀")
        }

        authRoutes(userService, jwtService)
        friendRoutes(friendService)
        drinkRoutes(drinkService, userService)
        notificationRoutes(notificationService)
        deviceRoutes(deviceService)
    }
}
