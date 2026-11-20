package org.erbeenjoyers.drinkwater

import com.russhwolf.settings.Settings
import kotlinx.datetime.Instant
import org.erbeenjoyers.drinkwater.api.ReminderSettings

/** Keeps the [ReminderSettings] on the device. They belong to the device, not the account. */
class ReminderStore(private val settings: Settings) {
    fun load(): ReminderSettings {
        val defaults = ReminderSettings()
        val startHour = settings.getInt(START_HOUR, defaults.startHour).coerceIn(0, 22)
        return ReminderSettings(
            enabled = settings.getBoolean(ENABLED, defaults.enabled),
            intervalMinutes = settings.getInt(INTERVAL, defaults.intervalMinutes).coerceAtLeast(1),
            startHour = startHour,
            endHour = settings.getInt(END_HOUR, defaults.endHour).coerceIn(startHour + 1, 23),
        )
    }

    fun save(reminders: ReminderSettings) {
        settings.putBoolean(ENABLED, reminders.enabled)
        settings.putInt(INTERVAL, reminders.intervalMinutes)
        settings.putInt(START_HOUR, reminders.startHour)
        settings.putInt(END_HOUR, reminders.endHour)
    }

    private companion object {
        const val ENABLED = "reminders.enabled"
        const val INTERVAL = "reminders.intervalMinutes"
        const val START_HOUR = "reminders.startHour"
        const val END_HOUR = "reminders.endHour"
    }
}

/** The platform's side of reminders: showing a notification at a given time, also when the app is closed. */
interface ReminderScheduler {
    /** Replaces the pending reminder with one at [at], or with none when [at] is null. */
    fun schedule(at: Instant?)
}
