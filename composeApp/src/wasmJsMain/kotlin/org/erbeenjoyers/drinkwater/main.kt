@file:OptIn(ExperimentalWasmJsInterop::class)

package org.erbeenjoyers.drinkwater

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.russhwolf.settings.StorageSettings
import kotlinx.browser.document
import kotlinx.browser.window
import org.erbeenjoyers.drinkwater.client.DrinkWaterApi

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
    val settings = StorageSettings()
    // Its own client: the push key is fetched before anyone logs in, and needs no token.
    val keyClient = DrinkWaterApi(server.url)
    val push = WebPush(settings) { keyClient.webPushKey() }
    val invite = inviteCodeFromFragment(window.location.hash)
    // Out of the address bar once read, so a reload or a bookmark doesn't carry the invite around.
    if (invite != null) window.history.replaceState(null, "", window.location.pathname + window.location.search)
    ComposeViewport(document.body!!) {
        App(
            settings,
            server,
            push = push,
            notificationPrompt = push,
            invite = invite,
            shareLink = if (canShareLinks()) ::shareLink else null,
        )
    }
}

/** The share sheet: Safari and Chrome on phones, Safari and Edge on desktops; Firefox and Chrome on Linux have none. */
private fun canShareLinks(): Boolean = js("typeof navigator.share === 'function'")

// Called straight from the click: browsers only open the share sheet in answer to one. Cancelling it rejects.
private fun shareLink(url: String): Unit = js("navigator.share({ url: url }).catch(function () {})")
