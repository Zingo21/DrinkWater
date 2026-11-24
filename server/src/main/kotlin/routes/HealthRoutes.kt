package routes

import database.Db
import io.ktor.http.CacheControl
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException
import org.erbeenjoyers.drinkwater.api.ServerInfo
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("routes.Health")

/**
 * For Docker's health check and uptime monitors: 200 when the server answers and can reach its database,
 * 503 when it can't.
 */
fun Route.healthRoutes(db: Db) {
    get("/health") {
        call.response.cacheControl(CacheControl.NoStore(null))
        try {
            db.query { exec("SELECT 1") }
            call.respondText("OK")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Health check failed", e)
            call.respondText("Database unavailable", status = HttpStatusCode.ServiceUnavailable)
        }
    }
}

/**
 * The version this server runs, for the apps to show (the web app comes from this server, so it is that version
 * too). It discloses nothing the public repository doesn't, and saying which release is live helps more than hiding
 * it: whether an update has arrived, or which version a bug report is about.
 */
fun Route.versionRoutes(version: String) {
    get("/version") {
        call.response.cacheControl(CacheControl.NoStore(null))
        call.respond(ServerInfo(version))
    }
}
