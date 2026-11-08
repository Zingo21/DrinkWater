package org.erbeenjoyers.drinkwater.ui

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/** Local time of day as `HH:mm`. */
fun Instant.clockTime(): String {
    val time = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

/** `HH:mm` for a moment earlier today, otherwise with the day in front: `8 Oct 14:05`. */
fun Instant.dayAndClockTime(): String {
    val zone = TimeZone.currentSystemDefault()
    val date = toLocalDateTime(zone).date
    return if (date == Clock.System.todayIn(zone)) clockTime() else "${date.dayAndMonth()} ${clockTime()}"
}

/** `Mon`, `Tue`, … */
fun LocalDate.shortWeekday(): String = dayOfWeek.name.shortName()

/** `8 Oct` */
fun LocalDate.dayAndMonth(): String = "$dayOfMonth ${month.name.shortName()}"

private fun String.shortName(): String = take(3).lowercase().replaceFirstChar { it.uppercase() }
