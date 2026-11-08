package org.erbeenjoyers.drinkwater

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemindersTest {
    private val zone = TimeZone.of("Europe/Stockholm")

    /** Every two hours between 08:00 and 22:00. */
    private val settings = ReminderSettings(enabled = true)

    private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDateTime(2026, 10, day, hour, minute).toInstant(zone)

    private fun next(now: Instant, lastDrinkAt: Instant?, goalReached: Boolean = false) =
        nextReminder(settings, now, lastDrinkAt, goalReached, zone)

    @Test
    fun noReminderWhenTurnedOff() {
        assertNull(nextReminder(ReminderSettings(enabled = false), at(8, 12), at(8, 11), false, zone))
    }

    @Test
    fun remindsOneIntervalAfterTheLastDrink() {
        assertEquals(at(8, 12, 30), next(now = at(8, 10, 45), lastDrinkAt = at(8, 10, 30)))
    }

    @Test
    fun keepsTheSameRhythmWhenAskedAgainLater() {
        assertEquals(at(8, 14, 30), next(now = at(8, 12, 31), lastDrinkAt = at(8, 10, 30)))
        assertEquals(at(8, 14, 30), next(now = at(8, 14, 29), lastDrinkAt = at(8, 10, 30)))
        // Never at the current moment, always ahead of it.
        assertEquals(at(8, 16, 30), next(now = at(8, 14, 30), lastDrinkAt = at(8, 10, 30)))
    }

    @Test
    fun countsFromTheStartHourOnADayWithoutDrinks() {
        assertEquals(at(8, 14), next(now = at(8, 13, 10), lastDrinkAt = null))
        assertEquals(at(8, 8), next(now = at(8, 6), lastDrinkAt = null))
    }

    @Test
    fun staysQuietAtNight() {
        assertEquals(at(9, 8), next(now = at(8, 21, 15), lastDrinkAt = at(8, 21)))
        assertEquals(at(8, 8), next(now = at(8, 1), lastDrinkAt = at(8, 0, 30)))
        // 22:00 itself is still allowed.
        assertEquals(at(8, 22), next(now = at(8, 20, 5), lastDrinkAt = at(8, 20)))
    }

    @Test
    fun aNewDayStartsOverAtTheStartHour() {
        assertEquals(at(9, 10), next(now = at(9, 8), lastDrinkAt = at(8, 21)))
        assertEquals(at(9, 10), next(now = at(9, 9, 15), lastDrinkAt = at(8, 21)))
        assertEquals(at(9, 11), next(now = at(9, 9, 15), lastDrinkAt = at(9, 9)))
    }

    @Test
    fun waitsUntilTomorrowOnceTheGoalIsReached() {
        assertEquals(at(9, 8), next(now = at(8, 13), lastDrinkAt = at(8, 12, 50), goalReached = true))
    }

    @Test
    fun aDrinkLoggedInTheFutureIsIgnored() {
        // The server's clock can be a little ahead of the phone's.
        assertEquals(at(8, 14), next(now = at(8, 12), lastDrinkAt = at(8, 12, 1)))
    }
}
