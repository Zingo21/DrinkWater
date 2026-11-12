package config

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

data class AppConfig(
    val databaseUrl: String,
    val jwt: JwtConfig,
    /** Path to the Firebase service account key used to send push notifications. Push is off when null. */
    val fcmServiceAccountFile: String? = null,
    /** The built web app (index.html and the files next to it), served at `/`. Not served when null. */
    val webAppDir: File? = null,
) {
    companion object {
        private const val DEV_JWT_SECRET = "dev-only-secret-change-me"

        /** Where `./gradlew :composeApp:wasmJsBrowserDistribution` puts the web app, seen from `./gradlew :server:run`. */
        private const val DEV_WEB_APP_DIR = "../composeApp/build/dist/wasmJs/productionExecutable"

        /** `WEB_APP_DIR`, or the web app built in this checkout if there is one. */
        private fun webAppDirFromEnvironment(): File? {
            System.getenv("WEB_APP_DIR")?.let { path ->
                val dir = File(path)
                require(File(dir, "index.html").isFile) { "WEB_APP_DIR has no index.html: ${dir.absolutePath}" }
                return dir
            }
            return File(DEV_WEB_APP_DIR).takeIf { File(it, "index.html").isFile }
        }

        fun databaseUrlFromEnvironment(): String = System.getenv("DATABASE_URL") ?: "jdbc:sqlite:drinkwater.db"

        /**
         * Reads `DATABASE_URL`, `JWT_SECRET`, `FCM_SERVICE_ACCOUNT_FILE` and `WEB_APP_DIR` from the environment. Outside development
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
                databaseUrl = databaseUrlFromEnvironment(),
                jwt = JwtConfig(secret = secret),
                fcmServiceAccountFile = System.getenv("FCM_SERVICE_ACCOUNT_FILE"),
                webAppDir = webAppDirFromEnvironment(),
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
