package org.erbeenjoyers.drinkwater.api

import kotlinx.serialization.Serializable

@Serializable
enum class DevicePlatform { ANDROID, IOS, WEB }

/**
 * [token] is the device's Firebase Cloud Messaging registration token, or for [DevicePlatform.WEB] the
 * browser's push subscription as `JSON.stringify(subscription)` gives it.
 */
@Serializable
data class DeviceRegistration(val token: String, val platform: DevicePlatform)

/** The server's VAPID public key (base64url): the `applicationServerKey` browsers subscribe with. */
@Serializable
data class WebPushKeyDto(val publicKey: String)
