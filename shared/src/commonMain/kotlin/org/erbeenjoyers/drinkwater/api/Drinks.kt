package org.erbeenjoyers.drinkwater.api

import kotlinx.serialization.Serializable

/** Amount logged when the client doesn't say how much was drunk. */
const val DEFAULT_AMOUNT_ML = 250

/** Largest amount that can be logged at once. */
const val MAX_AMOUNT_ML = 5_000

/** Daily goal of a user who hasn't chosen one. */
const val DEFAULT_DAILY_GOAL_ML = 2_000

const val MIN_DAILY_GOAL_ML = 250
const val MAX_DAILY_GOAL_ML = 10_000

@Serializable
data class DrinkDto(val id: Int, val name: String)

/**
 * [timeZone] is the user's IANA time zone, e.g. `Europe/Stockholm`. The server uses it to decide
 * what counts as "today" when checking the daily goal; without it days follow UTC.
 */
@Serializable
data class DrinkLogRequest(
    val drinkId: Int,
    val amountMl: Int = DEFAULT_AMOUNT_ML,
    val timeZone: String? = null,
)

/** [goalReached] is true when this drink took the user's total for today past their daily goal. */
@Serializable
data class DrinkLogDto(
    val id: Int,
    val userId: Int,
    val drinkId: Int,
    val timestamp: Long,
    val amountMl: Int = DEFAULT_AMOUNT_ML,
    val goalReached: Boolean = false,
)

/** One row of the user's own drink history. */
@Serializable
data class DrinkLogEntryDto(val id: Int, val drink: DrinkDto, val amountMl: Int, val timestamp: Long)

/** [date] is an ISO date, `yyyy-MM-dd`, in the time zone the stats were requested for. */
@Serializable
data class DayTotalDto(val date: String, val totalMl: Int)

/**
 * @property streakDays days in a row the goal was reached, ending today if today's goal is
 *   already reached and otherwise yesterday. Measured against the current [goalMl].
 * @property days totals for the last seven days, oldest first, ending with today.
 */
@Serializable
data class StatsDto(
    val goalMl: Int,
    val todayMl: Int,
    val streakDays: Int,
    val days: List<DayTotalDto>,
)

@Serializable
data class GoalDto(val dailyGoalMl: Int)
