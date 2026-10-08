package org.erbeenjoyers.drinkwater

import androidx.compose.ui.window.ComposeUIViewController
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings

/** The simulator shares the Mac's network, so a server started with `./gradlew :server:run` is on localhost. */
private const val SERVER_URL = "http://localhost:$SERVER_PORT"

@OptIn(ExperimentalSettingsImplementation::class)
private val settings by lazy { KeychainSettings(service = "org.erbeenjoyers.drinkwater") }

fun MainViewController() = ComposeUIViewController { App(settings, SERVER_URL) }
