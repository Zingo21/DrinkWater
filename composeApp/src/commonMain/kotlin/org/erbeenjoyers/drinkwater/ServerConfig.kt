package org.erbeenjoyers.drinkwater

import com.russhwolf.settings.Settings

/**
 * The server release builds talk to, on both Android and iOS. Must be `https://`: release builds
 * don't allow plain HTTP. Change it to your own domain before publishing the app.
 */
const val RELEASE_SERVER_URL = "https://drinkwater.example.com"

/**
 * Which server the app talks to. [url] is where it starts; when [editable] the user can point the
 * app at another server from the login screen, which only debug builds allow.
 */
data class ServerConfig(val url: String, val editable: Boolean) {
    companion object {
        /**
         * Release builds always use [RELEASE_SERVER_URL]. Debug builds start on [devServerUrl], set
         * at build time, and can be pointed elsewhere for testing on a real device.
         */
        fun forBuild(isDebug: Boolean, devServerUrl: String): ServerConfig =
            if (isDebug) ServerConfig(devServerUrl, editable = true) else ServerConfig(RELEASE_SERVER_URL, editable = false)
    }
}

private val serverUrlPattern = Regex("^https?://[a-z0-9.\\-\\[\\]:]+$", RegexOption.IGNORE_CASE)

/** Returns [input] as a server root like `http://192.168.1.20:8080`, or null if it isn't one. */
fun normalizeServerUrl(input: String): String? {
    val trimmed = input.trim().trimEnd('/')
    return trimmed.takeIf { serverUrlPattern.matches(it) }
}

/** Remembers a server picked on the login screen, for debug builds where [ServerConfig.editable] is true. */
class ServerUrlStore(private val settings: Settings) {
    fun load(): String? = settings.getStringOrNull(KEY)?.let(::normalizeServerUrl)

    fun save(url: String) {
        settings.putString(KEY, url)
    }

    fun clear() {
        settings.remove(KEY)
    }

    private companion object {
        const val KEY = "server_url"
    }
}
