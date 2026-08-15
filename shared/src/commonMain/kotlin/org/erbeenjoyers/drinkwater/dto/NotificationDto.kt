package org.erbeenjoyers.drinkwater.dto

import kotlinx.serialization.Serializable

/**
 * Typ av notifiering som skickas till en vän.
 * - DRANK: en vän har druckit vatten
 * - YOUR_TURN: det är mottagarens tur att dricka
 */
@Serializable
enum class NotificationType { DRANK, YOUR_TURN }

/**
 * En notifiering till en vän. Skapas när en användare dricker och skickas både
 * via WebSocket (push) och sparas så att appen kan hämta olästa notifieringar
 * vid uppstart.
 */
@Serializable
data class NotificationDto(
    val id: Int,
    val toUserId: Int,
    val fromUserId: Int,
    val fromUsername: String,
    val type: NotificationType,
    val message: String,
    val timestamp: Long,
    val read: Boolean = false
)

@Serializable
data class CreateNotificationRequest(
    val fromUserId: Int,
    val toUserId: Int,
    val type: NotificationType,
    val message: String
)
