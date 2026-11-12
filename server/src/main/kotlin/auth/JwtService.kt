package auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import config.JwtConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.request.ApplicationRequest
import org.erbeenjoyers.drinkwater.api.NOTIFICATIONS_PROTOCOL
import java.util.Date

/** The authenticated user, available in routes via `call.principal<UserPrincipal>()`. */
data class UserPrincipal(val userId: Int)

class JwtService(private val config: JwtConfig) {
    private val algorithm = Algorithm.HMAC256(config.secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()

    /** [tokenVersion] is the user's current [database.Users.tokenVersion]. */
    fun createToken(userId: Int, tokenVersion: Int): String = JWT.create()
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .withSubject(userId.toString())
        .withIssuedAt(Date())
        .withClaim(TOKEN_VERSION_CLAIM, tokenVersion)
        .withExpiresAt(Date(System.currentTimeMillis() + config.tokenLifetime.inWholeMilliseconds))
        .sign(algorithm)
}

/** Tokens from before this claim existed have none, which counts as version 0. */
const val TOKEN_VERSION_CLAIM = "ver"

/**
 * The token a browser sends as the WebSocket subprotocols `drinkwater, <token>`, as a Bearer header.
 * See [NOTIFICATIONS_PROTOCOL]; null when the request doesn't offer it.
 */
fun ApplicationRequest.webSocketProtocolToken(): HttpAuthHeader? {
    // Browsers send one comma-separated header; other clients may send a header per protocol.
    val protocols = headers.getAll(HttpHeaders.SecWebSocketProtocol).orEmpty().flatMap { it.split(',') }.map { it.trim() }
    if (protocols.size != 2 || protocols[0] != NOTIFICATIONS_PROTOCOL) return null
    return HttpAuthHeader.Single(AuthScheme.Bearer, protocols[1])
}
