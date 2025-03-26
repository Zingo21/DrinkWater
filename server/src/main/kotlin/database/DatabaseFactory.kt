package database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        Database.connect("jdbc:sqlite:drinkwater.db", driver = "org.sqlite.JDBC")

        transaction {
            SchemaUtils.create(Users, Drinks, DrinkLogs)
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