package org.erbeenjoyers.drinkwater

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import com.russhwolf.settings.Settings

/** Light or dark, as the user picked under Appearance; [System] follows the phone or computer, also when it changes. */
enum class ThemeChoice(val label: String) {
    System("Same as the device"),
    Light("Light"),
    Dark("Dark"),
}

/** Keeps the [ThemeChoice] on the device, like the reminder settings: it belongs to the device, not the account. */
class ThemeStore(private val settings: Settings) {
    fun load(): ThemeChoice =
        settings.getStringOrNull(KEY)?.let { stored -> ThemeChoice.entries.firstOrNull { it.name == stored } } ?: ThemeChoice.System

    fun save(choice: ThemeChoice) {
        settings.putString(KEY, choice.name)
    }

    private companion object {
        const val KEY = "theme"
    }
}

/** Material's own palettes: the purple the app has always had, and its dark counterpart. */
private val LightColors = lightColors()
private val DarkColors = darkColors()

@Composable
fun DrinkWaterTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    MaterialTheme(colors = if (dark) DarkColors else LightColors, content = content)
}
