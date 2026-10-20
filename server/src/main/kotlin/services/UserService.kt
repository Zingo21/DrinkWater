package services

import auth.PasswordHasher
import database.Db
import database.DeviceTokens
import database.DrinkLogs
import database.Friendships
import database.Users
import io.ktor.http.HttpStatusCode
import org.erbeenjoyers.drinkwater.api.GoalDto
import org.erbeenjoyers.drinkwater.api.MAX_DAILY_GOAL_ML
import org.erbeenjoyers.drinkwater.api.MIN_DAILY_GOAL_ML
import org.erbeenjoyers.drinkwater.api.UserDto
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class UserService(private val db: Db) {
    private val usernamePattern = Regex("^[a-z0-9_.]{3,30}$")

    suspend fun register(username: String, password: String): UserDto {
        val normalized = normalize(username)
        if (!usernamePattern.matches(normalized)) {
            throw ApiException(
                HttpStatusCode.BadRequest,
                "Username must be 3-30 characters: letters, digits, '_' or '.'",
            )
        }
        validatePassword(password)
        val hash = PasswordHasher.hash(password)

        return db.query {
            if (!Users.selectAll().where { Users.username eq normalized }.empty()) {
                throw ApiException(HttpStatusCode.Conflict, "Username is already taken")
            }
            val id = Users.insert {
                it[Users.username] = normalized
                it[passwordHash] = hash
                it[createdAt] = System.currentTimeMillis()
            } get Users.id
            UserDto(id, normalized)
        }
    }

    /** Returns the user if the credentials are valid, otherwise throws 401. */
    suspend fun authenticate(username: String, password: String): UserDto {
        val row = db.query {
            Users.selectAll().where { Users.username eq normalize(username) }.singleOrNull()
        }
        if (row == null || !PasswordHasher.verify(password, row[Users.passwordHash])) {
            throw ApiException(HttpStatusCode.Unauthorized, "Wrong username or password")
        }
        return row.toUserDto()
    }

    suspend fun findById(id: Int): UserDto? = db.query {
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUserDto()
    }

    suspend fun findByUsername(username: String): UserDto? = db.query {
        Users.selectAll().where { Users.username eq normalize(username) }.singleOrNull()?.toUserDto()
    }

    suspend fun setDailyGoal(userId: Int, dailyGoalMl: Int): GoalDto {
        if (dailyGoalMl !in MIN_DAILY_GOAL_ML..MAX_DAILY_GOAL_ML) {
            throw ApiException(
                HttpStatusCode.BadRequest,
                "Daily goal must be between $MIN_DAILY_GOAL_ML and $MAX_DAILY_GOAL_ML ml",
            )
        }
        db.query {
            Users.update({ Users.id eq userId }) { it[Users.dailyGoalMl] = dailyGoalMl }
        }
        return GoalDto(dailyGoalMl)
    }

    /** The current [Users.tokenVersion] of [userId], or null if there is no such user. */
    suspend fun tokenVersion(userId: Int): Int? = db.query {
        Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.tokenVersion)
    }

    /** Changes the password after checking the current one. Tokens issued until now stop working. */
    suspend fun changePassword(userId: Int, currentPassword: String, newPassword: String) {
        checkPassword(userId, currentPassword)
        validatePassword(newPassword)
        setPassword(userId, newPassword)
    }

    /**
     * Gives [username] a new password, for when they are locked out. Run by whoever runs the server,
     * see `reset-password` in Commands.kt. Returns false if there is no such user.
     */
    suspend fun resetPassword(username: String, newPassword: String): Boolean {
        validatePassword(newPassword)
        val user = findByUsername(username) ?: return false
        setPassword(user.id, newPassword)
        return true
    }

    /** Deletes the user and everything that belongs to them, after checking their password. */
    suspend fun deleteAccount(userId: Int, password: String) {
        checkPassword(userId, password)
        db.query {
            DeviceTokens.deleteWhere { DeviceTokens.userId eq userId }
            DrinkLogs.deleteWhere { DrinkLogs.userId eq userId }
            Friendships.deleteWhere { (requesterId eq userId) or (addresseeId eq userId) }
            Users.deleteWhere { Users.id eq userId }
        }
    }

    /**
     * Throws 403 rather than 401 on a wrong password: the user is logged in, they just typed the
     * wrong password, and the app treats 401 as an expired session.
     */
    private suspend fun checkPassword(userId: Int, password: String) {
        val hash = db.query { Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.passwordHash) }
            ?: throw ApiException(HttpStatusCode.NotFound, "User not found")
        if (!PasswordHasher.verify(password, hash)) {
            throw ApiException(HttpStatusCode.Forbidden, "Wrong password")
        }
    }

    private suspend fun setPassword(userId: Int, password: String) {
        val hash = PasswordHasher.hash(password)
        db.query {
            Users.update({ Users.id eq userId }) {
                it[passwordHash] = hash
                it[tokenVersion] = tokenVersion + 1
            }
        }
    }

    private fun validatePassword(password: String) {
        // bcrypt only uses the first 72 bytes of the password.
        if (password.length < 8 || password.toByteArray().size > 72) {
            throw ApiException(HttpStatusCode.BadRequest, "Password must be between 8 and 72 characters")
        }
    }

    private fun normalize(username: String) = username.trim().lowercase()
}

fun ResultRow.toUserDto() = UserDto(this[Users.id], this[Users.username])
