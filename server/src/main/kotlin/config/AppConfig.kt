package config

import org.erbeenjoyers.drinkwater.SERVER_PORT
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
    /** Address the app and API listen on; `127.0.0.1` when only Tailscale Funnel should reach them. */
    val host: String = "0.0.0.0",
    /** The admin panel, or null to not run one. */
    val admin: AdminConfig? = null,
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

        /** The admin panel runs only when `ADMIN_TAILSCALE_USERS` names someone who may use it. */
        private fun adminFromEnvironment(): AdminConfig? {
            val logins = System.getenv("ADMIN_TAILSCALE_USERS").orEmpty()
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            if (logins.isEmpty()) return null
            val port = System.getenv("ADMIN_PORT")?.toInt() ?: AdminConfig.DEFAULT_PORT
            require(port != SERVER_PORT) { "ADMIN_PORT must differ from the app's port $SERVER_PORT" }
            return AdminConfig(port, logins)
        }

        fun databaseUrlFromEnvironment(): String = System.getenv("DATABASE_URL") ?: "jdbc:sqlite:drinkwater.db"

        /**
         * Reads `DATABASE_URL`, `JWT_SECRET`, `FCM_SERVICE_ACCOUNT_FILE`, `WEB_APP_DIR`, `HOST`, `ADMIN_PORT` and
         * `ADMIN_TAILSCALE_USERS` from the environment. Outside development
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
                host = System.getenv("HOST") ?: "0.0.0.0",
                admin = adminFromEnvironment(),
            )
        }
    }
}

/**
 * The admin panel listens on 127.0.0.1:[port] only, for `tailscale serve` to make reachable inside the tailnet.
 * [allowedLogins] are the Tailscale login names (as in `Tailscale-User-Login`) that may use it.
 */
data class AdminConfig(val port: Int, val allowedLogins: Set<String>) {
    companion object {
        const val DEFAULT_PORT = 8090
    }
}

data class JwtConfig(
    val secret: String,
    val issuer: String = "drinkwater-server",
    val audience: String = "drinkwater-app",
    val realm: String = "DrinkWater",
    val tokenLifetime: Duration = 30.days,
)
