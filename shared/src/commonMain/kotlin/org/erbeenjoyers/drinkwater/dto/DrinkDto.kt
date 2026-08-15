package org.erbeenjoyers.drinkwater.dto

import kotlinx.serialization.Serializable

/**
 * En dryck som kan loggas. Servern seedar några standarddrycker (vatten etc.)
 * och appen listar dem via GET /drinks.
 */
@Serializable
data class DrinkDto(
    val id: Int,
    val name: String
)

@Serializable
data class DrinkLogDto(
    val id: Int,
    val userId: Int,
    val drinkId: Int,
    val drinkName: String,
    val timestamp: Long
)

@Serializable
data class DrinkLogRequest(
    val userId: Int,
    val drinkId: Int
)
