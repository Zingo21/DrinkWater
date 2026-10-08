package routes

import auth.UserPrincipal
import io.ktor.server.auth.principal
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.RoutingCall
import services.ApiException

/** Only call inside an `authenticate { }` block, where a principal is guaranteed. */
val RoutingCall.userId: Int
    get() = principal<UserPrincipal>()!!.userId

fun RoutingCall.intParameter(name: String): Int =
    parameters[name]?.toIntOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "Invalid $name")
