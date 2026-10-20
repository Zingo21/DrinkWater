package org.erbeenjoyers.drinkwater

import androidx.compose.ui.window.ComposeUIViewController
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import platform.Foundation.NSBundle
import kotlin.experimental.ExperimentalNativeApi

/**
 * The server debug builds start on, from `DevServerUrl` in Info.plist (set through
 * `DEV_SERVER_URL` in Config.xcconfig). The simulator shares the Mac's network, so by default
 * that is a server started with `./gradlew :server:run` on localhost.
 */
private fun devServerUrl(): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("DevServerUrl") as? String)
        ?.let(::normalizeServerUrl)
        ?: "http://localhost:$SERVER_PORT"

@OptIn(ExperimentalNativeApi::class)
private val server by lazy { ServerConfig.forBuild(kotlin.native.Platform.isDebugBinary, devServerUrl()) }

@OptIn(ExperimentalSettingsImplementation::class)
private val settings by lazy { KeychainSettings(service = "org.erbeenjoyers.drinkwater") }

fun MainViewController() = ComposeUIViewController { App(settings, server) }
