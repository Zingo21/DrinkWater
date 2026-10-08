package org.erbeenjoyers.drinkwater

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

private const val PREFS_NAME = "session"

/** Preferences encrypted with a key from the Android Keystore, used to store the login token. */
fun createSecureSettings(context: Context): Settings {
    val preferences = try {
        encryptedPreferences(context)
    } catch (e: Exception) {
        // The file can't be decrypted when its Keystore key is gone, e.g. after the app's data
        // was restored from a backup onto another device. Start over; the user logs in again.
        context.deleteSharedPreferences(PREFS_NAME)
        encryptedPreferences(context)
    }
    return SharedPreferencesSettings(preferences)
}

private fun encryptedPreferences(context: Context): SharedPreferences =
    EncryptedSharedPreferences.create(
        PREFS_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
