package routes

import auth.UserPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingCall

/** Only call inside an `authenticate { }` block, where a principal is guaranteed. */
val RoutingCall.userId: Int
    get() = principal<UserPrincipal>()!!.userId
