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
