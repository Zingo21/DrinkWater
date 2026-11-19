package services

import database.Db
import database.DeviceTokens
import io.ktor.http.HttpStatusCode
import org.erbeenjoyers.drinkwater.api.DevicePlatform
import org.erbeenjoyers.drinkwater.api.DeviceRegistration
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import push.Device
import push.WebPushSubscription

/** Keeps track of which devices should get a user's push notifications. */
class DeviceService(private val db: Db) {
    /**
     * Makes [registration]'s device receive [userId]'s pushes. A device belongs to one user at a time,
     * so registering it again after someone else logged in on it moves it to them.
     */
    suspend fun register(userId: Int, registration: DeviceRegistration) {
        val token = registration.token
        if (token.isBlank() || token.length > MAX_TOKEN_LENGTH) {
            throw ApiException(HttpStatusCode.BadRequest, "Invalid device token")
        }
        if (registration.platform == DevicePlatform.WEB && WebPushSubscription.parse(token) == null) {
            throw ApiException(HttpStatusCode.BadRequest, "Invalid web push subscription")
        }
        db.query {
            val updated = DeviceTokens.update({ DeviceTokens.token eq token }) {
                it[DeviceTokens.userId] = userId
                it[platform] = registration.platform
                it[updatedAt] = System.currentTimeMillis()
            }
            if (updated == 0) {
                DeviceTokens.insert {
                    it[DeviceTokens.userId] = userId
                    it[DeviceTokens.token] = token
                    it[platform] = registration.platform
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
        }
    }

    /** Stops pushes to one of [userId]'s devices, e.g. when they log out on it. Unknown tokens are ignored. */
    suspend fun unregister(userId: Int, token: String) {
        db.query {
            DeviceTokens.deleteWhere { (DeviceTokens.token eq token) and (DeviceTokens.userId eq userId) }
        }
    }

    /** Forgets a token the push service reported as no longer valid. */
    suspend fun forget(token: String) {
        db.query {
            DeviceTokens.deleteWhere { DeviceTokens.token eq token }
        }
    }

    suspend fun devices(userId: Int): List<Device> = db.query {
        DeviceTokens.selectAll().where { DeviceTokens.userId eq userId }
            .map { Device(it[DeviceTokens.token], it[DeviceTokens.platform]) }
    }

    private companion object {
        const val MAX_TOKEN_LENGTH = 2048
    }
}
