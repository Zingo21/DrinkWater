package org.erbeenjoyers.drinkwater

actual object ServerConfig {
    // Android-emulatorn når host-maskinen via 10.0.2.2
    actual val baseUrl: String = "http://10.0.2.2:$SERVER_PORT"
}
