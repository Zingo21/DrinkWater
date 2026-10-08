package database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
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
            val tables = arrayOf(Users, Drinks, DrinkLogs, Friendships, DeviceTokens)
            SchemaUtils.create(*tables)
            addMissingColumns(*tables)
            if (Drinks.selectAll().empty()) {
                defaultDrinks.forEach { drinkName -> Drinks.insert { it[name] = drinkName } }
            }
        }

        println("✅ Database initialized successfully!")
        return Db(database)
    }

    /**
     * Adds columns that were introduced after a database was first created. Only plain additions are
     * handled, which is all SQLite can do in place, so new columns need a default for existing rows.
     */
    private fun Transaction.addMissingColumns(vararg tables: Table) {
        val existing = currentDialect.tableColumns(*tables)
        tables.forEach { table ->
            val names = existing[table].orEmpty().map { it.name.lowercase() }
            table.columns
                .filter { it.name.lowercase() !in names }
                .forEach { column -> column.ddl.forEach { exec(it) } }
        }
    }
}
