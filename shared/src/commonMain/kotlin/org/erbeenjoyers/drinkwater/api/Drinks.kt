package org.erbeenjoyers.drinkwater.api

import kotlinx.serialization.Serializable

@Serializable
data class DrinkDto(val id: Int, val name: String)

@Serializable
data class DrinkLogRequest(val drinkId: Int)

@Serializable
data class DrinkLogDto(val id: Int, val userId: Int, val drinkId: Int, val timestamp: Long)
