package org.erbeenjoyers.drinkwater

actual object ServerConfig {
    // JVM (t.ex. enhetstester på servern) når host-maskinen via localhost
    actual val baseUrl: String = "http://localhost:$SERVER_PORT"
}
