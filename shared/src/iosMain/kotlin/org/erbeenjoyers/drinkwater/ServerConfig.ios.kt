package org.erbeenjoyers.drinkwater

actual object ServerConfig {
    // iOS-simulatorn når host-maskinen via localhost
    actual val baseUrl: String = "http://localhost:$SERVER_PORT"
}
