package org.erbeenjoyers.drinkwater

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.russhwolf.settings.StorageSettings
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalWasmJsInterop::class)
@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule

// Referencing the module makes the bundler include it, which registers the time zone rules.
private val jsJodaTz = JsJodaTimeZoneModule

/**
 * The web app. The DrinkWater server serves it, so the server is always the page's own origin and,
 * unlike the native apps, the app is never pointed anywhere else.
 *
 * The session lives in the browser's localStorage, readable by any script on this origin; the app
 * loads no third-party scripts, which is what keeps that acceptable.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val server = ServerConfig(url = window.location.origin, editable = false)
    ComposeViewport(document.body!!) {
        App(StorageSettings(), server)
    }
}
