package org.erbeenjoyers.drinkwater.api

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val username: String, val password: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

/** Returned on successful register/login. Send [token] as `Authorization: Bearer <token>`. */
@Serializable
data class AuthResponse(val token: String, val user: UserDto)

@Serializable
data class UserDto(val id: Int, val username: String)

@Serializable
data class ErrorResponse(val error: String)

/** Answered with a new [AuthResponse]: changing the password logs the account out everywhere else. */
@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

/** The password is asked for again so that a phone left unlocked can't be used to delete the account. */
@Serializable
data class DeleteAccountRequest(val password: String)
