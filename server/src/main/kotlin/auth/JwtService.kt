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

    fun createToken(userId: Int): String = JWT.create()
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .withSubject(userId.toString())
        .withExpiresAt(Date(System.currentTimeMillis() + config.tokenLifetime.inWholeMilliseconds))
        .sign(algorithm)
}
