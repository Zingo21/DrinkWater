package org.erbeenjoyers.drinkwater

import com.russhwolf.settings.Settings

/**
 * Which server the app talks to. [url] is where it starts; when [editable] the user can point the
 * app at another server from the login screen. [httpsOnly] servers are all a release build can reach.
 */
data class ServerConfig(val url: String, val editable: Boolean, val httpsOnly: Boolean = false) {
    companion object {
        /**
         * Release builds start on [releaseServerUrl] and can switch to another `https://` server, e.g. when
         * friends run their own. Debug builds start on [devServerUrl] and can also use plain HTTP, for testing
         * against a computer on the same network. Both are set at build time, so the domain isn't in the code.
         * A release build without a usable server address starts on none, and the user picks one on the login screen.
         */
        fun forBuild(isDebug: Boolean, devServerUrl: String, releaseServerUrl: String): ServerConfig =
            if (isDebug) {
                ServerConfig(devServerUrl, editable = true)
            } else {
                ServerConfig(normalizeServerUrl(releaseServerUrl, httpsOnly = true).orEmpty(), editable = true, httpsOnly = true)
            }
    }
}

private val serverUrlPattern = Regex("^https?://[a-z0-9.\\-\\[\\]:]+$", RegexOption.IGNORE_CASE)

/**
 * Returns [input] as a server root like `http://192.168.1.20:8080`, or null if it isn't one, or isn't
 * `https://` when [httpsOnly].
 */
fun normalizeServerUrl(input: String, httpsOnly: Boolean = false): String? {
    val trimmed = input.trim().trimEnd('/')
    if (httpsOnly && !trimmed.startsWith("https://", ignoreCase = true)) return null
    return trimmed.takeIf { serverUrlPattern.matches(it) }
}

/** Remembers a server picked on the login screen, where [ServerConfig.editable] is true. */
class ServerUrlStore(private val settings: Settings) {
    /** The picked server, unless it is plain HTTP and [httpsOnly] says the build can't reach it. */
    fun load(httpsOnly: Boolean = false): String? =
        settings.getStringOrNull(KEY)?.let { normalizeServerUrl(it, httpsOnly) }

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
