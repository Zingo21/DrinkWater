package org.erbeenjoyers.drinkwater.api

import kotlinx.serialization.Serializable

@Serializable
enum class DevicePlatform { ANDROID, IOS, WEB }

/**
 * [token] is the device's Firebase Cloud Messaging registration token, or for [DevicePlatform.WEB] the
 * browser's push subscription as `JSON.stringify(subscription)` gives it.
 *
 * [reminders], when given, makes the server push this device's drink reminders, following the days in
 * [timeZone] (an IANA id, default UTC). Each registration replaces the previous one's, so null turns them off.
 * Only the web app sends them; the Android app schedules its own.
 */
@Serializable
data class DeviceRegistration(
    val token: String,
    val platform: DevicePlatform,
    val reminders: ReminderSettings? = null,
    val timeZone: String? = null,
)

/** The server's VAPID public key (base64url): the `applicationServerKey` browsers subscribe with. */
@Serializable
data class WebPushKeyDto(val publicKey: String)
