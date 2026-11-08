package org.erbeenjoyers.drinkwater.api

import kotlinx.serialization.Serializable

@Serializable
data class FriendRequestCreate(val username: String)

@Serializable
data class FriendRequestDto(
    val id: Int,
    val from: UserDto,
    val to: UserDto,
    val createdAt: Long,
)

@Serializable
data class FriendRequestsResponse(
    val incoming: List<FriendRequestDto>,
    val outgoing: List<FriendRequestDto>,
)

@Serializable
enum class FriendRequestResult { SENT, ACCEPTED }

@Serializable
data class FriendRequestCreateResponse(val result: FriendRequestResult, val request: FriendRequestDto)

/**
 * One row of the leaderboard: how much [user] has drunk today and their streak, measured against
 * their own daily goal.
 */
@Serializable
data class LeaderboardEntryDto(val user: UserDto, val todayMl: Int, val goalMl: Int, val streakDays: Int)
