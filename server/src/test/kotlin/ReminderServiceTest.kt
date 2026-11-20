import database.DatabaseFactory
import database.Db
import database.DrinkLogs
import database.Drinks
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.erbeenjoyers.drinkwater.api.DevicePlatform
import org.erbeenjoyers.drinkwater.api.DeviceRegistration
import org.erbeenjoyers.drinkwater.api.ReminderSettings
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import push.Device
import push.PushMessage
import push.PushResult
import push.PushSender
import services.DeviceService
import services.REMINDER_MESSAGE
import services.ReminderService
import services.UserService
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class ReminderServiceTest {
    private val dbFile = File.createTempFile("drinkwater-reminders", ".db")
    private val db: Db = DatabaseFactory.init("jdbc:sqlite:${dbFile.absolutePath}")
    private val users = UserService(db)
    private val devices = DeviceService(db)
    private val stockholm = TimeZone.of("Europe/Stockholm")

    /** Records pushes. Tokens in [dead] are reported as no longer registered. */
    private class RecordingPush(private val dead: Set<String> = emptySet()) : PushSender {
        val sent = mutableListOf<Pair<String, PushMessage>>()

        override suspend fun send(device: Device, message: PushMessage): PushResult {
            sent += device.token to message
            return if (device.token in dead) PushResult.UNREGISTERED else PushResult.SENT
        }
    }

    private val push = RecordingPush(dead = setOf("dead-browser"))
    private val reminders = ReminderService(db, devices, push)

    @AfterTest
    fun deleteDatabase() {
        dbFile.delete()
    }

    /** 2026-10-[day] at [hour]:[minute] in Stockholm. */
    private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDateTime(2026, 10, day, hour, minute).toInstant(stockholm)

    private suspend fun user(name: String): Int = users.register(name, "password123").id

    /** Every two hours between 08:00 and 22:00 unless [settings] says otherwise. */
    private suspend fun browser(
        userId: Int,
        token: String,
        settings: ReminderSettings? = ReminderSettings(enabled = true),
        zone: String? = "Europe/Stockholm",
    ) = devices.register(userId, DeviceRegistration(token, DevicePlatform.ANDROID, settings, zone))

    private suspend fun drink(userId: Int, at: Instant, amountMl: Int = 250) = db.query {
        val water = Drinks.selectAll().first()[Drinks.id]
        DrinkLogs.insert {
            it[DrinkLogs.userId] = userId
            it[drinkId] = water
            it[timestamp] = at.toEpochMilliseconds()
            it[DrinkLogs.amountMl] = amountMl
        }
    }

    /** The tokens reminded in the minute up to [at]. */
    private suspend fun remindedAt(at: Instant): List<String> {
        push.sent.clear()
        reminders.sendDue(after = at - 1.minutes, upTo = at)
        return push.sent.map { (token, message) ->
            assertEquals(REMINDER_MESSAGE, message)
            token
        }
    }

    @Test
    fun `reminds one interval into a day without drinks, then every interval`() = runBlocking {
        browser(user("anna"), "browser")
        assertEquals(emptyList(), remindedAt(at(8, 9, 59)))
        assertEquals(listOf("browser"), remindedAt(at(8, 10)))
        assertEquals(emptyList(), remindedAt(at(8, 10, 1)))
        assertEquals(listOf("browser"), remindedAt(at(8, 12)))
    }

    @Test
    fun `a drink moves the next reminder`() = runBlocking {
        val anna = user("anna")
        browser(anna, "browser")
        drink(anna, at(8, 9, 30))
        assertEquals(emptyList(), remindedAt(at(8, 9, 30)), "no reminder right after a drink")
        assertEquals(emptyList(), remindedAt(at(8, 10)))
        assertEquals(listOf("browser"), remindedAt(at(8, 11, 30)))
    }

    @Test
    fun `stays quiet outside the hours and once the goal is reached`() = runBlocking {
        val anna = user("anna")
        browser(anna, "browser", ReminderSettings(enabled = true, intervalMinutes = 60, startHour = 9, endHour = 21))
        assertEquals(emptyList(), remindedAt(at(8, 8)))
        assertEquals(listOf("browser"), remindedAt(at(8, 20)))
        assertEquals(emptyList(), remindedAt(at(8, 22)))

        drink(anna, at(9, 9), amountMl = 2000)
        assertEquals(emptyList(), remindedAt(at(9, 10)), "the default goal is 2000 ml")
        assertEquals(listOf("browser"), remindedAt(at(10, 10)), "and the next day starts over")
    }

    @Test
    fun `follows each browser's own settings and time zone`() = runBlocking {
        val anna = user("anna")
        browser(anna, "stockholm")
        browser(anna, "london", zone = "Europe/London")
        browser(anna, "turned-off", settings = ReminderSettings(enabled = false))
        browser(anna, "phone", settings = null)
        browser(user("bertil"), "utc", zone = null)

        // 10:00 in Stockholm is 09:00 in London and 08:00 UTC.
        assertEquals(listOf("stockholm"), remindedAt(at(8, 10)))
        assertEquals(listOf("london"), remindedAt(at(8, 11)))
        assertEquals(listOf("stockholm", "utc"), remindedAt(at(8, 12)).sorted())
    }

    @Test
    fun `forgets browsers the push service no longer knows`() = runBlocking {
        val anna = user("anna")
        browser(anna, "dead-browser")
        assertEquals(listOf("dead-browser"), remindedAt(at(8, 10)))
        assertEquals(emptyList(), devices.devices(anna))
    }
}
