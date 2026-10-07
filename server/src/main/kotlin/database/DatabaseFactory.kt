package database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import java.sql.Connection

/** Wraps the Exposed [Database] so every query runs on the IO dispatcher. */
class Db(val database: Database) {
    suspend fun <T> query(block: Transaction.() -> T): T =
        withContext(Dispatchers.IO) { transaction(database) { block() } }
}

object DatabaseFactory {
    private val defaultDrinks = listOf("Vatten")

    fun init(jdbcUrl: String): Db {
        val database = Database.connect(jdbcUrl, driver = "org.sqlite.JDBC")
        database.transactionManager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

        transaction(database) {
            SchemaUtils.create(Users, Drinks, DrinkLogs, Friendships)
            if (Drinks.selectAll().empty()) {
                defaultDrinks.forEach { drinkName -> Drinks.insert { it[name] = drinkName } }
            }
        }

        println("✅ Database initialized successfully!")
        return Db(database)
    }
}
