package org.erbeenjoyers.drinkwater.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Messages the server pushes over the `/notifications` WebSocket, encoded as JSON. */
@Serializable
sealed interface Notification {
    @Serializable
    @SerialName("friend_drank")
    data class FriendDrank(
        val friend: UserDto,
        val drink: DrinkDto,
        val timestamp: Long,
        val amountMl: Int = DEFAULT_AMOUNT_ML,
    ) : Notification

    /** Sent when a friend's total for the day passes their daily goal of [goalMl]. */
    @Serializable
    @SerialName("friend_reached_goal")
    data class FriendReachedGoal(val friend: UserDto, val goalMl: Int) : Notification

    /** [friend] wants to remind you to drink. */
    @Serializable
    @SerialName("nudge")
    data class Nudge(val friend: UserDto) : Notification

    @Serializable
    @SerialName("friend_request")
    data class FriendRequestReceived(val request: FriendRequestDto) : Notification

    @Serializable
    @SerialName("friend_request_accepted")
    data class FriendRequestAccepted(val friend: UserDto) : Notification
}

/** A one-line description of [this], used in the app's feed and as the text of push notifications. */
fun Notification.describe(): String = when (this) {
    is Notification.FriendDrank -> "${friend.username} drank $amountMl ml ${drink.name}"
    is Notification.FriendReachedGoal -> "${friend.username} reached their daily goal of $goalMl ml"
    is Notification.FriendRequestReceived -> "${request.from.username} sent you a friend request"
    is Notification.FriendRequestAccepted -> "${friend.username} accepted your friend request"
    is Notification.Nudge -> "${friend.username} nudged you: time for a drink of water"
}
