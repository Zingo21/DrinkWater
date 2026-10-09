package org.erbeenjoyers.drinkwater

const val SERVER_PORT = 8080

/**
 * Bas-URL till Ktor-servern. Används av mobilappens ktor-client.
 *
 * Android-emulatorn når host-maskinen via 10.0.2.2. iOS-simulatorn når
 * host-maskinen via localhost. Byt vid behov mot serverns publika adress.
 */
expect object ServerConfig {
    val baseUrl: String
}
