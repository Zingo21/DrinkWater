package org.erbeenjoyers.drinkwater

import com.russhwolf.settings.Settings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.erbeenjoyers.drinkwater.api.AuthResponse

/**
 * Remembers the logged-in user and their token between app starts. [settings] must be
 * backed by secure storage (Keystore-encrypted preferences on Android, the Keychain on iOS).
 */
class SessionStore(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AuthResponse? {
        val stored = settings.getStringOrNull(KEY) ?: return null
        return try {
            json.decodeFromString(AuthResponse.serializer(), stored)
        } catch (e: SerializationException) {
            // Written by an incompatible version of the app; the user just has to log in again.
            clear()
            null
        }
    }

    fun save(session: AuthResponse) {
        settings.putString(KEY, json.encodeToString(AuthResponse.serializer(), session))
    }

    fun clear() {
        settings.remove(KEY)
    }

    private companion object {
        const val KEY = "session"
    }
}
