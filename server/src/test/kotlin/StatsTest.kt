import database.DatabaseFactory
import database.DrinkLogs
import database.Users
import org.erbeenjoyers.drinkwater.api.DEFAULT_AMOUNT_ML
import org.erbeenjoyers.drinkwater.api.DEFAULT_DAILY_GOAL_ML
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import services.LoggedAmount
import services.computeStats
import java.io.File
import java.sql.DriverManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsTest {
    private val zone = ZoneId.of("Europe/Stockholm")
    private val today = LocalDate.of(2026, 10, 8)

    private fun log(daysAgo: Long, hour: Int, amountMl: Int) = LoggedAmount(
        LocalDateTime.of(today.minusDays(daysAgo), java.time.LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli(),
        amountMl,
    )

    @Test
    fun `sums each day and lists the last week`() {
        val stats = computeStats(listOf(log(0, 9, 250), log(0, 15, 500), log(2, 12, 330)), 2000, zone, today)

        assertEquals(750, stats.todayMl)
        assertEquals(listOf(0, 0, 0, 0, 330, 0, 750), stats.days.map { it.totalMl })
        assertEquals("2026-10-02", stats.days.first().date)
        assertEquals("2026-10-08", stats.days.last().date)
    }

    @Test
    fun `days follow the requested time zone`() {
        // 23:00 in Stockholm is already the next day in Tokyo.
        val lateEvening = log(1, 23, 400)

        assertEquals(0, computeStats(listOf(lateEvening), 2000, zone, today).todayMl)
        assertEquals(400, computeStats(listOf(lateEvening), 2000, ZoneId.of("Asia/Tokyo"), today).todayMl)
    }

    @Test
    fun `streak counts today only once its goal is reached`() {
        val threeFullDays = listOf(log(1, 10, 1000), log(2, 10, 600), log(2, 18, 400), log(3, 10, 1200))

        // Today isn't over, so not having reached the goal yet doesn't break the streak.
        assertEquals(3, computeStats(threeFullDays + log(0, 8, 250), 1000, zone, today).streakDays)
        assertEquals(4, computeStats(threeFullDays + log(0, 8, 1000), 1000, zone, today).streakDays)
    }

    @Test
    fun `streak stops at the first missed day`() {
        val logs = listOf(log(1, 10, 1000), log(2, 10, 999), log(3, 10, 1000))

        assertEquals(1, computeStats(logs, 1000, zone, today).streakDays)
        assertEquals(0, computeStats(listOf(log(2, 10, 1000)), 1000, zone, today).streakDays)
    }

    @Test
    fun `a database from before amounts and goals is upgraded in place`() {
        val dbFile = File.createTempFile("drinkwater-old", ".db")
        try {
            val url = "jdbc:sqlite:${dbFile.absolutePath}"
            DriverManager.getConnection(url).use { connection ->
                connection.createStatement().use {
                    it.executeUpdate(
                        "CREATE TABLE Users (id INTEGER PRIMARY KEY AUTOINCREMENT, username VARCHAR(30) NOT NULL, " +
                            "password_hash VARCHAR(100) NOT NULL, created_at BIGINT NOT NULL)",
                    )
                    it.executeUpdate("CREATE TABLE Drinks (id INTEGER PRIMARY KEY AUTOINCREMENT, \"name\" VARCHAR(50) NOT NULL)")
                    it.executeUpdate(
                        "CREATE TABLE DrinkLogs (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INT NOT NULL, " +
                            "drink_id INT NOT NULL, \"timestamp\" BIGINT NOT NULL)",
                    )
                    it.executeUpdate("INSERT INTO Users (username, password_hash, created_at) VALUES ('anna', 'x', 1)")
                    it.executeUpdate("INSERT INTO Drinks (\"name\") VALUES ('Vatten')")
                    it.executeUpdate("INSERT INTO DrinkLogs (user_id, drink_id, \"timestamp\") VALUES (1, 1, 1)")
                }
            }

            val db = DatabaseFactory.init(url)

            transaction(db.database) {
                assertEquals(DEFAULT_DAILY_GOAL_ML, Users.selectAll().single()[Users.dailyGoalMl])
                assertEquals(DEFAULT_AMOUNT_ML, DrinkLogs.selectAll().single()[DrinkLogs.amountMl])
            }
        } finally {
            dbFile.delete()
        }
    }
}
