package services

import database.Db
import database.DrinkLogs
import database.Drinks
import database.Users
import io.ktor.http.HttpStatusCode
import org.erbeenjoyers.drinkwater.api.DrinkDto
import org.erbeenjoyers.drinkwater.api.DrinkLogDto
import org.erbeenjoyers.drinkwater.api.DrinkLogEntryDto
import org.erbeenjoyers.drinkwater.api.DrinkLogRequest
import org.erbeenjoyers.drinkwater.api.LeaderboardEntryDto
import org.erbeenjoyers.drinkwater.api.MAX_AMOUNT_ML
import org.erbeenjoyers.drinkwater.api.Notification
import org.erbeenjoyers.drinkwater.api.StatsDto
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import java.time.Instant

class DrinkService(
    private val db: Db,
    private val users: UserService,
    private val friends: FriendService,
    private val notifications: NotificationService,
) {
    suspend fun drinks(): List<DrinkDto> = db.query {
        Drinks.selectAll().orderBy(Drinks.id).map { DrinkDto(it[Drinks.id], it[Drinks.name]) }
    }

    /** Logs a drink for [userId] and notifies that user's friends. */
    suspend fun logDrink(userId: Int, request: DrinkLogRequest): DrinkLogDto {
        if (request.amountMl !in 1..MAX_AMOUNT_ML) {
            throw ApiException(HttpStatusCode.BadRequest, "Amount must be between 1 and $MAX_AMOUNT_ML ml")
        }
        val zone = parseTimeZone(request.timeZone)
        val timestamp = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

        val (drink, goalMl, log) = db.query {
            val drink = Drinks.selectAll().where { Drinks.id eq request.drinkId }.singleOrNull()
                ?.let { DrinkDto(it[Drinks.id], it[Drinks.name]) }
                ?: throw ApiException(HttpStatusCode.NotFound, "Drink not found")
            val goalMl = dailyGoal(userId)
            val total = DrinkLogs.amountMl.sum()
            val before = DrinkLogs.select(total)
                .where { (DrinkLogs.userId eq userId) and (DrinkLogs.timestamp greaterEq startOfDayMillis(today, zone)) }
                .single()[total] ?: 0

            val id = DrinkLogs.insert {
                it[DrinkLogs.userId] = userId
                it[drinkId] = request.drinkId
                it[DrinkLogs.timestamp] = timestamp
                it[amountMl] = request.amountMl
            } get DrinkLogs.id
            val goalReached = before < goalMl && before + request.amountMl >= goalMl
            Triple(drink, goalMl, DrinkLogDto(id, userId, request.drinkId, timestamp, request.amountMl, goalReached))
        }

        val user = users.findById(userId)
        if (user != null) {
            val friendIds = friends.friendIds(userId)
            notifications.sendToAll(friendIds, Notification.FriendDrank(user, drink, timestamp, request.amountMl))
            if (log.goalReached) {
                notifications.sendToAll(friendIds, Notification.FriendReachedGoal(user, goalMl))
            }
        }
        return log
    }

    /** [userId]'s own logs, newest first. [from] is inclusive and [to] exclusive, both in epoch milliseconds. */
    suspend fun history(userId: Int, from: Long?, to: Long?, limit: Int): List<DrinkLogEntryDto> = db.query {
        val query = (DrinkLogs innerJoin Drinks).selectAll().where { DrinkLogs.userId eq userId }
        if (from != null) query.andWhere { DrinkLogs.timestamp greaterEq from }
        if (to != null) query.andWhere { DrinkLogs.timestamp less to }
        query.orderBy(DrinkLogs.timestamp to SortOrder.DESC, DrinkLogs.id to SortOrder.DESC)
            .limit(limit)
            .map {
                DrinkLogEntryDto(
                    id = it[DrinkLogs.id],
                    drink = DrinkDto(it[Drinks.id], it[Drinks.name]),
                    amountMl = it[DrinkLogs.amountMl],
                    timestamp = it[DrinkLogs.timestamp],
                )
            }
    }

    /** Removes one of [userId]'s own logs, e.g. one that was added by mistake. */
    suspend fun deleteLog(userId: Int, logId: Int) {
        val deleted = db.query {
            DrinkLogs.deleteWhere { (id eq logId) and (DrinkLogs.userId eq userId) }
        }
        if (deleted == 0) throw ApiException(HttpStatusCode.NotFound, "Drink log not found")
    }

    /** Today's total, the streak and the last week for [userId], with days as they fall in [timeZone]. */
    suspend fun stats(userId: Int, timeZone: String?): StatsDto {
        val zone = parseTimeZone(timeZone)
        val today = Instant.now().atZone(zone).toLocalDate()
        val since = startOfDayMillis(today.minusDays(STREAK_LOOKBACK_DAYS), zone)

        val (goalMl, logs) = db.query {
            val logs = DrinkLogs.select(DrinkLogs.timestamp, DrinkLogs.amountMl)
                .where { (DrinkLogs.userId eq userId) and (DrinkLogs.timestamp greaterEq since) }
                .map { LoggedAmount(it[DrinkLogs.timestamp], it[DrinkLogs.amountMl]) }
            dailyGoal(userId) to logs
        }
        return computeStats(logs, goalMl, zone, today)
    }

    /**
     * [userId] and their friends, whoever has drunk the most today first. Everyone's days are
     * drawn in [timeZone], the time zone of the user who is looking.
     */
    suspend fun leaderboard(userId: Int, timeZone: String?): List<LeaderboardEntryDto> {
        val zone = parseTimeZone(timeZone)
        val today = Instant.now().atZone(zone).toLocalDate()
        val since = startOfDayMillis(today.minusDays(STREAK_LOOKBACK_DAYS), zone)
        val ids = friends.friendIds(userId) + userId

        return db.query {
            val logs = DrinkLogs.select(DrinkLogs.userId, DrinkLogs.timestamp, DrinkLogs.amountMl)
                .where { (DrinkLogs.userId inList ids) and (DrinkLogs.timestamp greaterEq since) }
                .groupBy({ it[DrinkLogs.userId] }) { LoggedAmount(it[DrinkLogs.timestamp], it[DrinkLogs.amountMl]) }
            Users.selectAll().where { Users.id inList ids }
                .map {
                    val stats = computeStats(logs[it[Users.id]].orEmpty(), it[Users.dailyGoalMl], zone, today)
                    LeaderboardEntryDto(it.toUserDto(), stats.todayMl, stats.goalMl, stats.streakDays)
                }
                .sortedWith(
                    compareByDescending<LeaderboardEntryDto> { it.todayMl }
                        .thenByDescending { it.streakDays }
                        .thenBy { it.user.username },
                )
        }
    }

    private fun Transaction.dailyGoal(userId: Int): Int =
        Users.select(Users.dailyGoalMl).where { Users.id eq userId }.single()[Users.dailyGoalMl]
}
