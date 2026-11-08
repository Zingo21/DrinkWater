package org.erbeenjoyers.drinkwater

import com.russhwolf.settings.Settings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

/** The intervals a user can choose between, in minutes. */
val REMINDER_INTERVALS_MINUTES = listOf(30, 60, 90, 120, 180, 240)

/**
 * When this device reminds its user to drink: once [intervalMinutes] have passed without a logged
 * drink, but only between [startHour]:00 and [endHour]:00 so nobody is woken up.
 */
data class ReminderSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 120,
    val startHour: Int = 8,
    val endHour: Int = 22,
)

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

/**
 * When to remind next, or null when reminders are off.
 *
 * Reminders follow the last drink: one interval after it, then another interval after that and so
 * on, so asking again later gives the same answer. Each day starts over at the start hour, which
 * is also what a day without drinks counts from. A time outside the allowed hours moves to the
 * next start hour, and so does everything left of today once [goalReached].
 *
 * @param lastDrinkAt when the user last logged a drink, if known.
 */
fun nextReminder(
    settings: ReminderSettings,
    now: Instant,
    lastDrinkAt: Instant?,
    goalReached: Boolean,
    zone: TimeZone,
): Instant? {
    if (!settings.enabled) return null
    val today = now.toLocalDateTime(zone).date
    val start = LocalTime(settings.startHour, 0)
    val end = LocalTime(settings.endHour, 0)

    val startToday = today.atTime(start).toInstant(zone)
    val anchor = listOfNotNull(lastDrinkAt, startToday).filter { it <= now }.maxOrNull() ?: now
    val intervalsPassed = (now - anchor).inWholeMinutes / settings.intervalMinutes
    val candidate = anchor + settings.intervalMinutes.minutes * (intervalsPassed.toInt() + 1)

    val local = candidate.toLocalDateTime(zone)
    return when {
        local.time < start -> local.date.atTime(start).toInstant(zone)
        local.time > end || (goalReached && local.date == today) ->
            local.date.plus(1, DateTimeUnit.DAY).atTime(start).toInstant(zone)
        else -> candidate
    }
}
