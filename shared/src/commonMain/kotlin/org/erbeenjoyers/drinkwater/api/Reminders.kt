package org.erbeenjoyers.drinkwater.api

import kotlinx.serialization.Serializable

/** The intervals a user can choose between, in minutes. */
val REMINDER_INTERVALS_MINUTES = listOf(30, 60, 90, 120, 180, 240)

/**
 * When a device reminds its user to drink: once [intervalMinutes] have passed without a logged
 * drink, but only between [startHour]:00 and [endHour]:00 so nobody is woken up.
 *
 * They belong to the device, not the account. The Android app keeps them to itself and schedules its own
 * reminders; browsers can't, so the web app sends them along with its push subscription and the server
 * pushes the reminders (see [DeviceRegistration]).
 */
@Serializable
data class ReminderSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 120,
    val startHour: Int = 8,
    val endHour: Int = 22,
) {
    /** Whether these are settings [org.erbeenjoyers.drinkwater.reminders.nextReminder] can work with. */
    val isValid: Boolean
        get() = intervalMinutes in 1..MAX_REMINDER_INTERVAL_MINUTES && startHour in 0..22 && endHour in startHour + 1..23
}

const val MAX_REMINDER_INTERVAL_MINUTES = 24 * 60
