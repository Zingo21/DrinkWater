package database

import org.jetbrains.exposed.sql.Table

object Users : Table() {
    val id = integer("id").autoIncrement()
    /** Always stored lowercase so usernames are unique regardless of case. */
    val username = varchar("username", 30).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val createdAt = long("created_at")
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
