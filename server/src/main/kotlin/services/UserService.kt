package services

import auth.PasswordHasher
import database.Db
import database.Users
import io.ktor.http.HttpStatusCode
import org.erbeenjoyers.drinkwater.api.GoalDto
import org.erbeenjoyers.drinkwater.api.MAX_DAILY_GOAL_ML
import org.erbeenjoyers.drinkwater.api.MIN_DAILY_GOAL_ML
import org.erbeenjoyers.drinkwater.api.UserDto
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
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
        // bcrypt only uses the first 72 bytes of the password.
        if (password.length < 8 || password.toByteArray().size > 72) {
            throw ApiException(HttpStatusCode.BadRequest, "Password must be between 8 and 72 characters")
        }
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

    private fun normalize(username: String) = username.trim().lowercase()
}

fun ResultRow.toUserDto() = UserDto(this[Users.id], this[Users.username])
