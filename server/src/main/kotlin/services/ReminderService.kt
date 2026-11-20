package services

import database.Db
import database.DeviceTokens
import database.DrinkLogs
import database.Users
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.erbeenjoyers.drinkwater.api.ReminderSettings
import org.erbeenjoyers.drinkwater.reminders.nextReminder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import push.Device
import push.PushMessage
import push.PushResult
import push.PushSender
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

val REMINDER_MESSAGE = PushMessage(
    title = "Time for some water",
    body = "It's been a while since you logged a drink.",
    // A new reminder replaces one still on screen, and a reminder the phone only gets an hour later is no use.
    tag = "reminder",
    ttlSeconds = 3600,
)

/**
 * Pushes the drink reminders of devices that can't show them at a set time themselves (the web app), at the
 * times [nextReminder] gives, the same as the Android app reminds itself.
 *
 * Nothing is stored about which reminders went out: every [CHECK_PERIOD] [run] asks whether a device's next
 * reminder after the previous check has come by now. Reminders due while the server was down are skipped.
 */
class ReminderService(
    private val db: Db,
    private val devices: DeviceService,
    private val push: PushSender,
    private val clock: Clock = Clock.System,
) {
    private val log = LoggerFactory.getLogger(ReminderService::class.java)

    /** Checks for due reminders until cancelled. */
    suspend fun run() {
        if (!push.enabled) return
        var previous = clock.now()
        while (true) {
            delay(CHECK_PERIOD)
            val now = clock.now()
            try {
                sendDue(after = previous, upTo = now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Could not send reminders", e)
            }
            previous = now
        }
    }

    /** Pushes every reminder that falls after [after] and no later than [upTo]. */
    suspend fun sendDue(after: Instant, upTo: Instant) {
        for (device in dueDevices(after, upTo)) {
            if (push.send(device, REMINDER_MESSAGE) == PushResult.UNREGISTERED) devices.forget(device.token)
        }
    }

    private suspend fun dueDevices(after: Instant, upTo: Instant): List<Device> = db.query {
        val reminding = (DeviceTokens innerJoin Users).selectAll()
            .where { DeviceTokens.reminderIntervalMinutes.isNotNull() }
            .toList()
        if (reminding.isEmpty()) return@query emptyList()

        // Today in any time zone started less than LOG_LOOKBACK ago, and a drink before the start of today
        // doesn't move a reminder, so these are all the logs it takes.
        val since = (upTo - LOG_LOOKBACK).toEpochMilliseconds()
        val logs = DrinkLogs.selectAll()
            .where { (DrinkLogs.userId inList reminding.map { it[Users.id] }.distinct()) and (DrinkLogs.timestamp greater since) }
            .groupBy({ it[DrinkLogs.userId] }) { Instant.fromEpochMilliseconds(it[DrinkLogs.timestamp]) to it[DrinkLogs.amountMl] }

        reminding.filter { row ->
            val userLogs = logs[row[Users.id]].orEmpty()
            val lastDrinkAt = userLogs.maxOfOrNull { it.first }
            // Just drank: the next reminder is a whole interval away.
            if (lastDrinkAt != null && lastDrinkAt > after) return@filter false

            // Checked when it was registered, but the JVM's zone rules could have changed since.
            val zone = row[DeviceTokens.timeZone]?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.UTC
            val today = upTo.toLocalDateTime(zone).date
            val drunkToday = userLogs.filter { it.first.toLocalDateTime(zone).date == today }.sumOf { it.second }
            val settings = ReminderSettings(
                enabled = true,
                intervalMinutes = row[DeviceTokens.reminderIntervalMinutes]!!,
                startHour = row[DeviceTokens.reminderStartHour],
                endHour = row[DeviceTokens.reminderEndHour],
            )
            val next = nextReminder(settings, after, lastDrinkAt, drunkToday >= row[Users.dailyGoalMl], zone)
            next != null && next <= upTo
        }.map { Device(it[DeviceTokens.token], it[DeviceTokens.platform]) }
    }

    companion object {
        val CHECK_PERIOD: Duration = 1.minutes
        private val LOG_LOOKBACK: Duration = 48.hours
    }
}
