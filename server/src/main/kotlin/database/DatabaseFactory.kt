package database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import java.sql.Connection

object DatabaseFactory {
    fun init() {
        try {
            val database = Database.connect(
                "jdbc:sqlite:drinkwater.db",
                driver = "org.sqlite.JDBC"
            )
            database.transactionManager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
            transaction(database) {
                SchemaUtils.createMissingTablesAndColumns(Users, Drinks, DrinkLogs, Friends, Notifications)
                seedDrinksIfEmpty()
            }
            println("✅ Database initialized successfully!")
        } catch (e: Exception) {
            println("❌ Database initialization failed: ${e.localizedMessage}")
            e.printStackTrace()
        }
    }

    private fun seedDrinksIfEmpty() {
        val hasDrinks = Drinks.selectAll().count() > 0
        if (!hasDrinks) {
            Drinks.batchInsert(listOf("Vatten", "Lemonvatten", "Te", "Soda")) { name ->
                this[Drinks.name] = name
            }
            println("✅ Seeded default drinks")
        }
    }
}

object Users : Table() {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    val score = long("score").default(0)
    val isYourTurn = bool("is_your_turn").default(false)
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

/**
 * Vänrelationer. Varje vänlagning sparar två rader (a->b och b->a) så att båda
 * parter ser varandra i sin vänlista. Detta förenklar frågor och undviker att
 * man måste söka i båda riktningar vid listning.
 */
object Friends : Table() {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val friendId = integer("friend_id").references(Users.id)
    override val primaryKey = PrimaryKey(id)
    init {
        uniqueIndex("friends_user_friend_unique", userId, friendId)
    }
}

/**
 * Notifieringar till en vän. Skapas när en användare dricker vatten och skickas
 * även i realtid via WebSocket. Sparas så att appen kan hämta olästa vid uppstart.
 */
object Notifications : Table() {
    val id = integer("id").autoIncrement()
    val toUserId = integer("to_user_id").references(Users.id)
    val fromUserId = integer("from_user_id").references(Users.id)
    val type = varchar("type", 20)
    val message = varchar("message", 200)
    val timestamp = long("timestamp")
    val read = bool("read").default(false)
    override val primaryKey = PrimaryKey(id)
}
