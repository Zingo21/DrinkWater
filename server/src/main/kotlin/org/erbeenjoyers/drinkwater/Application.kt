package org.erbeenjoyers.drinkwater

import admin.startAdminPanel
import auth.JwtService
import auth.TOKEN_VERSION_CLAIM
import auth.UserPrincipal
import auth.webSocketProtocolToken
import config.AppConfig
import database.DatabaseFactory
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.erbeenjoyers.drinkwater.api.ErrorResponse
import push.PushSender
import routes.PASSWORD_RATE_LIMIT
import routes.authRoutes
import routes.deviceRoutes
import routes.drinkRoutes
import routes.friendRoutes
import routes.healthRoutes
import routes.notificationRoutes
import routes.versionRoutes
import routes.webApp
import services.AdminService
import services.ApiException
import services.DeviceService
import services.DrinkService
import services.FriendService
import services.NotificationService
import services.ReminderService
import services.UserService
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    if (args.isNotEmpty()) exitProcess(runCommand(args.toList()))

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

    // For the web app: its WebAssembly is several megabytes and shrinks to about a third with gzip,
    // and unchanged files are answered with 304 Not Modified.
    install(Compression) {
        gzip()
    }
    install(ConditionalHeaders)
    install(CachingHeaders)

    // Slows down password guessing: every route that checks a password shares this budget per client address.
    install(RateLimit) {
        register(PASSWORD_RATE_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call ->
                config.trustedClientIpHeader?.let { call.request.headers[it] } ?: call.request.origin.remoteHost
            }
        }
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

    // Runs in the application's scope, so it stops with the server.
    val reminderService = ReminderService(db, deviceService, push)
    launch { reminderService.run() }

    config.admin?.let { startAdminPanel(it, AdminService(db), userService, notificationService) }

    install(Authentication) {
        jwt {
            realm = config.jwt.realm
            verifier(jwtService.verifier)
            authHeader { call -> call.request.parseAuthorizationHeader() ?: call.request.webSocketProtocolToken() }
            validate { credential ->
                val userId = credential.payload.subject?.toIntOrNull()
                val version = credential.payload.getClaim(TOKEN_VERSION_CLAIM).asInt() ?: 0
                // Rejects tokens of deleted users and tokens from before the latest password change.
                if (userId != null && userService.tokenVersion(userId) == version) UserPrincipal(userId) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing or invalid token"))
            }
        }
    }

    routing {
        val webAppDir = config.webAppDir
        if (webAppDir != null) {
            webApp(webAppDir)
        } else {
            get("/") {
                call.respondText("Drink Water API is running 🚀")
            }
        }

        authRoutes(userService, jwtService, deviceService, notificationService)
        friendRoutes(friendService)
        drinkRoutes(drinkService, userService)
        notificationRoutes(notificationService)
        deviceRoutes(deviceService, push)
        healthRoutes(db)
        versionRoutes(config.version)
    }
}
