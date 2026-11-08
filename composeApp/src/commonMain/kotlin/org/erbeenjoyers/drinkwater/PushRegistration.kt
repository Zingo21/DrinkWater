package org.erbeenjoyers.drinkwater

import org.erbeenjoyers.drinkwater.api.DevicePlatform

/** The platform's side of push notifications: where this device's token comes from. */
interface PushRegistration {
    val platform: DevicePlatform

    /** This device's Firebase Cloud Messaging token, or null when push isn't set up or available. */
    suspend fun token(): String?
}
