package services

import io.ktor.http.HttpStatusCode
import org.erbeenjoyers.drinkwater.api.DayTotalDto
import org.erbeenjoyers.drinkwater.api.StatsDto
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** How far back logs are read for stats, which also caps how long a streak can be reported. */
const val STREAK_LOOKBACK_DAYS = 366L

private const val DAYS_IN_STATS = 7

data class LoggedAmount(val timestamp: Long, val amountMl: Int)

/** Parses an IANA zone id sent by a client. Days follow UTC when the client didn't send one. */
fun parseTimeZone(id: String?): ZoneId {
    if (id == null) return ZoneOffset.UTC
    return try {
        ZoneId.of(id)
    } catch (e: DateTimeException) {
        throw ApiException(HttpStatusCode.BadRequest, "Unknown time zone")
    }
}

fun startOfDayMillis(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

/** Sums [logs] per calendar day in [zone] and measures them against [goalMl], as seen on [today]. */
fun computeStats(logs: List<LoggedAmount>, goalMl: Int, zone: ZoneId, today: LocalDate): StatsDto {
    val totals = logs
        .groupingBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        .fold(0) { total, log -> total + log.amountMl }
    fun total(day: LocalDate) = totals[day] ?: 0

    // A day that isn't over yet doesn't break the streak, it just doesn't count until its goal is reached.
    var day = if (total(today) >= goalMl) today else today.minusDays(1)
    var streak = 0
    while (total(day) >= goalMl) {
        streak++
        day = day.minusDays(1)
    }

    return StatsDto(
        goalMl = goalMl,
        todayMl = total(today),
        streakDays = streak,
        days = (DAYS_IN_STATS - 1 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            DayTotalDto(date.toString(), total(date))
        },
    )
}
