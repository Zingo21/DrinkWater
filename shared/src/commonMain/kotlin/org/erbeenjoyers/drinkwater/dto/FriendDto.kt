package org.erbeenjoyers.drinkwater.dto

import kotlinx.serialization.Serializable

/**
 * En vänrelation mellan två användare. [userId] äger relationen och [friendId]
 * är vännen. Relationen är enkelriktad i datamodellen men vän-routes skapar
 * alltid en motsvarande post åt andra hållet så att båda ser varandra.
 */
@Serializable
data class FriendDto(
    val id: Int,
    val userId: Int,
    val friendId: Int,
    val friendUsername: String,
    val friendScore: Long = 0,
    val isFriendTurn: Boolean = false
)

@Serializable
data class AddFriendRequest(
    val userId: Int,
    val friendUsername: String
)

@Serializable
data class RemoveFriendRequest(
    val userId: Int,
    val friendId: Int
)
