package services

import database.Db
import database.FriendshipStatus
import database.Friendships
import database.Users
import io.ktor.http.HttpStatusCode
import org.erbeenjoyers.drinkwater.api.FriendRequestCreateResponse
import org.erbeenjoyers.drinkwater.api.FriendRequestDto
import org.erbeenjoyers.drinkwater.api.FriendRequestResult
import org.erbeenjoyers.drinkwater.api.FriendRequestsResponse
import org.erbeenjoyers.drinkwater.api.Notification
import org.erbeenjoyers.drinkwater.api.UserDto
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.concurrent.ConcurrentHashMap

const val NUDGE_COOLDOWN_MS = 5 * 60 * 1000L

class FriendService(
    private val db: Db,
    private val users: UserService,
    private val notifications: NotificationService,
) {
    /** When each (from, to) pair last nudged. */
    private val lastNudges = ConcurrentHashMap<Pair<Int, Int>, Long>()

    /**
     * Sends a friend request to [username]. If that user has already sent a request
     * to [fromUserId], it is accepted instead, so both users end up as friends.
     */
    suspend fun sendRequest(fromUserId: Int, username: String): FriendRequestCreateResponse {
        val target = users.findByUsername(username)
            ?: throw ApiException(HttpStatusCode.NotFound, "User not found")
        if (target.id == fromUserId) {
            throw ApiException(HttpStatusCode.BadRequest, "You can't add yourself as a friend")
        }

        val response = db.query {
            val existing = Friendships.selectAll().where { between(fromUserId, target.id) }.singleOrNull()
            when {
                existing == null -> {
                    val id = Friendships.insert {
                        it[requesterId] = fromUserId
                        it[addresseeId] = target.id
                        it[status] = FriendshipStatus.PENDING
                        it[createdAt] = System.currentTimeMillis()
                    } get Friendships.id
                    FriendRequestCreateResponse(FriendRequestResult.SENT, requestDto(id))
                }
                existing[Friendships.status] == FriendshipStatus.ACCEPTED ->
                    throw ApiException(HttpStatusCode.Conflict, "You are already friends")
                existing[Friendships.requesterId] == fromUserId ->
                    throw ApiException(HttpStatusCode.Conflict, "Friend request already sent")
                else -> {
                    val id = existing[Friendships.id]
                    Friendships.update({ Friendships.id eq id }) { it[status] = FriendshipStatus.ACCEPTED }
                    FriendRequestCreateResponse(FriendRequestResult.ACCEPTED, requestDto(id))
                }
            }
        }

        when (response.result) {
            FriendRequestResult.SENT ->
                notifications.send(target.id, Notification.FriendRequestReceived(response.request))
            FriendRequestResult.ACCEPTED ->
                notifications.send(target.id, Notification.FriendRequestAccepted(response.request.to))
        }
        return response
    }

    /** Accepts a pending request that was sent to [userId]. */
    suspend fun acceptRequest(userId: Int, requestId: Int): UserDto {
        val request = db.query {
            val row = pendingRequest(requestId)
            if (row == null || row[Friendships.addresseeId] != userId) {
                throw ApiException(HttpStatusCode.NotFound, "Friend request not found")
            }
            Friendships.update({ Friendships.id eq requestId }) { it[status] = FriendshipStatus.ACCEPTED }
            requestDto(requestId)
        }
        notifications.send(request.from.id, Notification.FriendRequestAccepted(request.to))
        return request.from
    }

    /** Declines a request sent to [userId], or cancels one that [userId] sent. */
    suspend fun deleteRequest(userId: Int, requestId: Int) {
        db.query {
            val row = pendingRequest(requestId)
            if (row == null || (row[Friendships.addresseeId] != userId && row[Friendships.requesterId] != userId)) {
                throw ApiException(HttpStatusCode.NotFound, "Friend request not found")
            }
            Friendships.deleteWhere { id eq requestId }
        }
    }

    suspend fun pendingRequests(userId: Int): FriendRequestsResponse = db.query {
        val rows = Friendships.selectAll()
            .where {
                (Friendships.status eq FriendshipStatus.PENDING) and
                    ((Friendships.requesterId eq userId) or (Friendships.addresseeId eq userId))
            }
            .orderBy(Friendships.createdAt)
            .toList()
        val requests = requestDtos(rows)
        FriendRequestsResponse(
            incoming = requests.filter { it.to.id == userId },
            outgoing = requests.filter { it.from.id == userId },
        )
    }

    suspend fun friends(userId: Int): List<UserDto> = db.query {
        val ids = friendIdsQuery(userId)
        usersById(ids).values.sortedBy { it.username }
    }

    suspend fun friendIds(userId: Int): List<Int> = db.query { friendIdsQuery(userId) }

    suspend fun removeFriend(userId: Int, friendId: Int) {
        val deleted = db.query {
            Friendships.deleteWhere {
                (status eq FriendshipStatus.ACCEPTED) and between(userId, friendId)
            }
        }
        if (deleted == 0) throw ApiException(HttpStatusCode.NotFound, "Friend not found")
    }

    /**
     * Reminds [friendId] to drink. To keep it friendly, the same friend can only be nudged once
     * every [NUDGE_COOLDOWN_MS]; the cooldowns are kept in memory and start over when the server restarts.
     */
    suspend fun nudge(userId: Int, friendId: Int) {
        val areFriends = db.query {
            !Friendships.selectAll()
                .where { (Friendships.status eq FriendshipStatus.ACCEPTED) and between(userId, friendId) }
                .empty()
        }
        val user = users.findById(userId)
        if (!areFriends || user == null) throw ApiException(HttpStatusCode.NotFound, "Friend not found")

        val now = System.currentTimeMillis()
        var allowed = false
        lastNudges.compute(userId to friendId) { _, last ->
            allowed = last == null || now - last >= NUDGE_COOLDOWN_MS
            if (allowed) now else last
        }
        if (!allowed) {
            throw ApiException(HttpStatusCode.TooManyRequests, "You nudged them just now. Give them a few minutes.")
        }
        notifications.send(friendId, Notification.Nudge(user))
    }

    private fun between(a: Int, b: Int): Op<Boolean> =
        ((Friendships.requesterId eq a) and (Friendships.addresseeId eq b)) or
            ((Friendships.requesterId eq b) and (Friendships.addresseeId eq a))

    private fun Transaction.friendIdsQuery(userId: Int): List<Int> =
        Friendships.selectAll()
            .where {
                (Friendships.status eq FriendshipStatus.ACCEPTED) and
                    ((Friendships.requesterId eq userId) or (Friendships.addresseeId eq userId))
            }
            .map {
                if (it[Friendships.requesterId] == userId) it[Friendships.addresseeId] else it[Friendships.requesterId]
            }

    private fun Transaction.pendingRequest(requestId: Int): ResultRow? =
        Friendships.selectAll()
            .where { (Friendships.id eq requestId) and (Friendships.status eq FriendshipStatus.PENDING) }
            .singleOrNull()

    private fun Transaction.requestDto(requestId: Int): FriendRequestDto =
        requestDtos(Friendships.selectAll().where { Friendships.id eq requestId }.toList()).single()

    private fun Transaction.usersById(ids: Collection<Int>): Map<Int, UserDto> =
        if (ids.isEmpty()) emptyMap()
        else Users.selectAll().where { Users.id inList ids }.associate { it[Users.id] to it.toUserDto() }

    private fun Transaction.requestDtos(rows: List<ResultRow>): List<FriendRequestDto> {
        val usersById = usersById(rows.flatMap { listOf(it[Friendships.requesterId], it[Friendships.addresseeId]) }.toSet())
        return rows.map {
            FriendRequestDto(
                id = it[Friendships.id],
                from = usersById.getValue(it[Friendships.requesterId]),
                to = usersById.getValue(it[Friendships.addresseeId]),
                createdAt = it[Friendships.createdAt],
            )
        }
    }
}
