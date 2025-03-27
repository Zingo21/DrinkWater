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
                SchemaUtils.createMissingTablesAndColumns(Users, Drinks, DrinkLogs)
            }

            println("✅ Database initialized successfully!")
        } catch (e: Exception) {
            println("❌ Database initialization failed: ${e.localizedMessage}")
            e.printStackTrace()
        }
    }
}


object Users : Table() {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50)
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