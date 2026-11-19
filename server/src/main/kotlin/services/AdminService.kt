package services

import database.Db
import database.DrinkLogs
import database.FriendshipStatus
import database.Friendships
import database.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

data class AdminOverview(val users: Int, val activeLast7Days: Int, val drinksLast24Hours: Int, val mlLast24Hours: Int)

data class AdminUser(
    val id: Int,
    val username: String,
    val createdAt: Long,
    /** Null if they have never logged a drink. */
    val lastDrinkAt: Long?,
    val drinksLast7Days: Int,
    val friends: Int,
)

/** Read-only views of all users for the admin panel; changes go through [UserService]. */
class AdminService(private val db: Db) {
    suspend fun overview(now: Long = System.currentTimeMillis()): AdminOverview = db.query {
        val weekAgo = now - 7.days.inWholeMilliseconds
        val dayAgo = now - 24.hours.inWholeMilliseconds
        val activeUsers = DrinkLogs.select(DrinkLogs.userId).where { DrinkLogs.timestamp greaterEq weekAgo }
            .withDistinct().count()
        val drinks = DrinkLogs.id.count()
        val ml = DrinkLogs.amountMl.sum()
        val lastDay = DrinkLogs.select(drinks, ml).where { DrinkLogs.timestamp greaterEq dayAgo }.single()
        AdminOverview(
            users = Users.selectAll().count().toInt(),
            activeLast7Days = activeUsers.toInt(),
            drinksLast24Hours = lastDay[drinks].toInt(),
            mlLast24Hours = lastDay[ml] ?: 0,
        )
    }

    /** Everyone, most recently active first; those who never logged a drink last. */
    suspend fun users(now: Long = System.currentTimeMillis()): List<AdminUser> = db.query {
        val lastDrink = DrinkLogs.timestamp.max()
        val lastDrinks = DrinkLogs.select(DrinkLogs.userId, lastDrink).groupBy(DrinkLogs.userId)
            .associate { it[DrinkLogs.userId] to it[lastDrink] }

        val count = DrinkLogs.id.count()
        val weekAgo = now - 7.days.inWholeMilliseconds
        val recentDrinks = DrinkLogs.select(DrinkLogs.userId, count).where { DrinkLogs.timestamp greaterEq weekAgo }
            .groupBy(DrinkLogs.userId)
            .associate { it[DrinkLogs.userId] to it[count].toInt() }

        val friends = mutableMapOf<Int, Int>()
        Friendships.selectAll().where { Friendships.status eq FriendshipStatus.ACCEPTED }.forEach {
            friends.merge(it[Friendships.requesterId], 1, Int::plus)
            friends.merge(it[Friendships.addresseeId], 1, Int::plus)
        }

        Users.selectAll()
            .map {
                val id = it[Users.id]
                AdminUser(
                    id = id,
                    username = it[Users.username],
                    createdAt = it[Users.createdAt],
                    lastDrinkAt = lastDrinks[id],
                    drinksLast7Days = recentDrinks[id] ?: 0,
                    friends = friends[id] ?: 0,
                )
            }
            .sortedWith(compareByDescending<AdminUser> { it.lastDrinkAt ?: Long.MIN_VALUE }.thenBy { it.username })
    }
}
