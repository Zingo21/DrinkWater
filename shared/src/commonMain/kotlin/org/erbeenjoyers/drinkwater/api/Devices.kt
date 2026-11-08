package org.erbeenjoyers.drinkwater.api

import kotlinx.serialization.Serializable

@Serializable
enum class DevicePlatform { ANDROID, IOS }

/** [token] is the device's Firebase Cloud Messaging registration token. */
@Serializable
data class DeviceRegistration(val token: String, val platform: DevicePlatform)
