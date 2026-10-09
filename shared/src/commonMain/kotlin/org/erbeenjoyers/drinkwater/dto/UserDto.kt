package org.erbeenjoyers.drinkwater.dto

import kotlinx.serialization.Serializable

/**
 * Användare i systemet. En användare skapas av appen och refereras i vänrelationer
 * och drick-loggar. Innehåller också fält för poäng och "tur"-status, så att
 * servern kan berätta för appen vems tur det är att dricka.
 */
@Serializable
data class UserDto(
    val id: Int,
    val username: String,
    val score: Long = 0,
    val isYourTurn: Boolean = false
)

@Serializable
data class CreateUserRequest(val username: String)
