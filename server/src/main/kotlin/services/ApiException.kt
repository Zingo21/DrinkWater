package services

import io.ktor.http.HttpStatusCode

/** Thrown by services; StatusPages turns it into an [org.erbeenjoyers.drinkwater.api.ErrorResponse]. */
class ApiException(val status: HttpStatusCode, override val message: String) : RuntimeException(message)
