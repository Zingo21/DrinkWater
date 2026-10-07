package config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

data class AppConfig(
    val databaseUrl: String,
    val jwt: JwtConfig,
) {
    companion object {
        private const val DEV_JWT_SECRET = "dev-only-secret-change-me"

        /**
         * Reads `DATABASE_URL` and `JWT_SECRET` from the environment. Outside development
         * mode `JWT_SECRET` is required, so a real deployment never signs tokens with the dev secret.
         */
        fun fromEnvironment(developmentMode: Boolean): AppConfig {
            val secret = System.getenv("JWT_SECRET")
                ?: if (developmentMode) {
                    println("⚠️ JWT_SECRET is not set, using an insecure development secret")
                    DEV_JWT_SECRET
                } else {
                    error("JWT_SECRET must be set when not running in development mode")
                }
            return AppConfig(
                databaseUrl = System.getenv("DATABASE_URL") ?: "jdbc:sqlite:drinkwater.db",
                jwt = JwtConfig(secret = secret),
            )
        }
    }
}

data class JwtConfig(
    val secret: String,
    val issuer: String = "drinkwater-server",
    val audience: String = "drinkwater-app",
    val realm: String = "DrinkWater",
    val tokenLifetime: Duration = 30.days,
)
