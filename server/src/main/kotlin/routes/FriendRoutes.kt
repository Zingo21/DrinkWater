package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.erbeenjoyers.drinkwater.api.FriendRequestCreate
import org.erbeenjoyers.drinkwater.api.FriendRequestResult
import services.FriendService

fun Route.friendRoutes(friends: FriendService) {
    authenticate {
        route("/friends") {
            get {
                call.respond(friends.friends(call.userId))
            }

            delete("/{friendId}") {
                friends.removeFriend(call.userId, call.intParameter("friendId"))
                call.respond(HttpStatusCode.NoContent)
            }

            post("/{friendId}/nudge") {
                friends.nudge(call.userId, call.intParameter("friendId"))
                call.respond(HttpStatusCode.NoContent)
            }

            route("/requests") {
                get {
                    call.respond(friends.pendingRequests(call.userId))
                }

                post {
                    val request = call.receive<FriendRequestCreate>()
                    val response = friends.sendRequest(call.userId, request.username)
                    val status = if (response.result == FriendRequestResult.SENT) HttpStatusCode.Created else HttpStatusCode.OK
                    call.respond(status, response)
                }

                post("/{requestId}/accept") {
                    call.respond(friends.acceptRequest(call.userId, call.intParameter("requestId")))
                }

                delete("/{requestId}") {
                    friends.deleteRequest(call.userId, call.intParameter("requestId"))
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
