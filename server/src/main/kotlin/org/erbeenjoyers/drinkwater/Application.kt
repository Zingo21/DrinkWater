package org.erbeenjoyers.drinkwater

import database.DatabaseFactory
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.json.Json
import routes.drinkRoutes
import routes.friendRoutes
import routes.notificationRoutes
import routes.pushRoutes
import routes.userRoutes
import routes.webSocketRoutes
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(WebSockets)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(CORS) {
        // Tillåt mobilappen (Android/iOS) att anropa servern oavsett ursprung.
        anyHost()
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respondText(
                "Bad request: ${cause.localizedMessage}",
                status = io.ktor.http.HttpStatusCode.BadRequest
            )
        }
        exception<NoSuchElementException> { call, cause ->
            call.respondText(
                "Not found: ${cause.localizedMessage}",
                status = io.ktor.http.HttpStatusCode.NotFound
            )
        }
    }
    DatabaseFactory.init()
    routing {
        get("/") {
            call.respondText("Drink Water API is running \uD83D\uDE80")
        }
        webSocketRoutes()
        pushRoutes()
        drinkRoutes()
        userRoutes()
        friendRoutes()
        notificationRoutes()
    }
}
