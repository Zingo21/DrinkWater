package services

import database.Db
import database.DrinkLogs
import database.Drinks
import io.ktor.http.HttpStatusCode
import org.erbeenjoyers.drinkwater.api.DrinkDto
import org.erbeenjoyers.drinkwater.api.DrinkLogDto
import org.erbeenjoyers.drinkwater.api.Notification
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

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
    suspend fun logDrink(userId: Int, drinkId: Int): DrinkLogDto {
        val timestamp = System.currentTimeMillis()
        val (drink, log) = db.query {
            val drink = Drinks.selectAll().where { Drinks.id eq drinkId }.singleOrNull()
                ?.let { DrinkDto(it[Drinks.id], it[Drinks.name]) }
                ?: throw ApiException(HttpStatusCode.NotFound, "Drink not found")
            val id = DrinkLogs.insert {
                it[DrinkLogs.userId] = userId
                it[DrinkLogs.drinkId] = drinkId
                it[DrinkLogs.timestamp] = timestamp
            } get DrinkLogs.id
            drink to DrinkLogDto(id, userId, drinkId, timestamp)
        }

        val user = users.findById(userId)
        if (user != null) {
            notifications.sendToAll(friends.friendIds(userId), Notification.FriendDrank(user, drink, timestamp))
        }
        return log
    }
}
