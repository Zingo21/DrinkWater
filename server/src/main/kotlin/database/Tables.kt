package database

import org.erbeenjoyers.drinkwater.api.DEFAULT_AMOUNT_ML
import org.erbeenjoyers.drinkwater.api.DEFAULT_DAILY_GOAL_ML
import org.erbeenjoyers.drinkwater.api.DevicePlatform
import org.jetbrains.exposed.sql.Table

object Users : Table() {
    val id = integer("id").autoIncrement()
    /** Always stored lowercase so usernames are unique regardless of case. */
    val username = varchar("username", 30).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val createdAt = long("created_at")
    val dailyGoalMl = integer("daily_goal_ml").default(DEFAULT_DAILY_GOAL_ML)
    override val primaryKey = PrimaryKey(id)
}

object Drinks : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50)
    override val primaryKey = PrimaryKey(id)
}

object DrinkLogs : Table() {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val drinkId = integer("drink_id").references(Drinks.id)
    val timestamp = long("timestamp")
    val amountMl = integer("amount_ml").default(DEFAULT_AMOUNT_ML)
    override val primaryKey = PrimaryKey(id)
}

enum class FriendshipStatus { PENDING, ACCEPTED }

/**
 * One row per pair of users. [requesterId] sent the request; while [status] is
 * PENDING only [addresseeId] can accept it. Once ACCEPTED the direction no longer matters.
 */
object Friendships : Table() {
    val id = integer("id").autoIncrement()
    val requesterId = integer("requester_id").references(Users.id)
    val addresseeId = integer("addressee_id").references(Users.id)
    val status = enumerationByName<FriendshipStatus>("status", 10)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(requesterId, addresseeId)
    }
}

/** Devices that get a user's push notifications. A device token belongs to one user at a time. */
object DeviceTokens : Table() {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id).index()
    val token = varchar("token", 512).uniqueIndex()
    val platform = enumerationByName<DevicePlatform>("platform", 10)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}
