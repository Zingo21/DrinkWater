package org.erbeenjoyers.drinkwater

import database.DatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.json.Json
import routes.drinkRoutes
import routes.pushRoutes
import routes.webSocketRoutes
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}


fun Application.module() {
    install(WebSockets)

    install(ContentNegotiation){
        json(Json { ignoreUnknownKeys = true })
    }

    DatabaseFactory.init()

    routing {
        get("/"){
            call.respondText("Drink Water API is running \uD83D\uDE80")
        }

        webSocketRoutes()
        pushRoutes()
        drinkRoutes()
    }
}