package auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import config.JwtConfig
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
