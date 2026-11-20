package org.erbeenjoyers.drinkwater.reminders

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.erbeenjoyers.drinkwater.api.ReminderSettings
import kotlin.time.Duration.Companion.minutes

/**
 * When to remind next, or null when reminders are off. Shared so that the Android app, which schedules its own
 * reminders, and the server, which pushes them to the web app, remind at the same times.
 *
 * Reminders follow the last drink: one interval after it, then another interval after that and so
 * on, so asking again later gives the same answer. Each day starts over at the start hour, which
 * is also what a day without drinks counts from. A time outside the allowed hours moves to the
 * next start hour, and so does everything left of today once [goalReached].
 *
 * The result is always after [now].
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
