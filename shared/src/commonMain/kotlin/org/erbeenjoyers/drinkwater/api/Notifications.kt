package org.erbeenjoyers.drinkwater.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Messages the server pushes over the `/notifications` WebSocket, encoded as JSON. */
@Serializable
sealed interface Notification {
    @Serializable
    @SerialName("friend_drank")
    data class FriendDrank(val friend: UserDto, val drink: DrinkDto, val timestamp: Long) : Notification

    @Serializable
    @SerialName("friend_request")
    data class FriendRequestReceived(val request: FriendRequestDto) : Notification

    @Serializable
    @SerialName("friend_request_accepted")
    data class FriendRequestAccepted(val friend: UserDto) : Notification
}
