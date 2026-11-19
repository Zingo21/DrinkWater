package admin

import auth.temporaryPassword
import config.AdminConfig
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtml
import io.ktor.server.netty.Netty
import io.ktor.server.response.header
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.html.BODY
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.TR
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.unsafe
import org.slf4j.LoggerFactory
import services.AdminService
import services.NotificationService
import services.UserService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Set by `tailscale serve` for people in the tailnet; spoofed copies are removed before it proxies the request. */
private const val TAILSCALE_LOGIN = "Tailscale-User-Login"

/** Set by Tailscale on requests that come in from the internet through Funnel. */
private const val TAILSCALE_FUNNEL = "Tailscale-Funnel-Request"

private val log = LoggerFactory.getLogger("admin")
private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

/**
 * The admin panel: a separate server that only the machine itself can reach, and `tailscale serve` makes
 * reachable inside the tailnet. It has no login of its own: Tailscale says who is asking, and only [AdminConfig.allowedLogins]
 * get in.
 */
fun Application.adminModule(
    config: AdminConfig,
    admin: AdminService,
    users: UserService,
    notifications: NotificationService,
) {
    install(TailscaleAccess) {
        allowedLogins = config.allowedLogins
    }

    routing {
        get("/") {
            val overview = admin.overview()
            val all = admin.users()
            call.respondHtml {
                page("DrinkWater admin") {
                    h1 { +"DrinkWater admin" }
                    call.request.queryParameters["deleted"]?.let { p("notice") { +"Deleted $it." } }
                    div("stats") {
                        stat(overview.users, "users")
                        stat(overview.activeLast7Days, "active last 7 days")
                        stat(overview.drinksLast24Hours, "drinks last 24 hours")
                        stat(overview.mlLast24Hours, "ml last 24 hours")
                    }
                    h2 { +"Users" }
                    table {
                        thead {
                            tr {
                                th { +"Username" }
                                th { +"Joined" }
                                th { +"Last drink" }
                                th { +"Drinks, 7 days" }
                                th { +"Friends" }
                                th { }
                            }
                        }
                        tbody {
                            all.forEach { user ->
                                tr {
                                    td { strong { +user.username } }
                                    labelled("Joined", formatTime(user.createdAt))
                                    labelled("Last drink", user.lastDrinkAt?.let(::formatTime) ?: "never")
                                    labelled("Drinks, 7 days", user.drinksLast7Days.toString())
                                    labelled("Friends", user.friends.toString())
                                    td("actions") {
                                        a("/users/${user.id}/reset-password") { +"Reset password" }
                                        +" · "
                                        a("/users/${user.id}/delete", classes = "danger") { +"Delete" }
                                    }
                                }
                            }
                        }
                    }
                    if (all.isEmpty()) p { +"Nobody has signed up yet." }
                    p("whoami") { +"Signed in through Tailscale as ${call.adminLogin}." }
                }
            }
        }

        get("/users/{id}/reset-password") {
            val user = users.findById(call.userId()) ?: return@get call.notFound()
            call.respondHtml {
                page("Reset password") {
                    h1 { +"Reset ${user.username}'s password?" }
                    p { +"They get a new, random password and are logged out on all their devices." }
                    form("/users/${user.id}/reset-password", method = FormMethod.post) {
                        button(type = ButtonType.submit) { +"Reset password" }
                        +" "
                        a("/") { +"Cancel" }
                    }
                }
            }
        }

        post("/users/{id}/reset-password") {
            val user = users.findById(call.userId()) ?: return@post call.notFound()
            val password = temporaryPassword()
            users.resetPassword(user.username, password)
            log.info("{} reset the password of {}", call.adminLogin, user.username)
            call.respondHtml {
                page("Password reset") {
                    h1 { +"New password for ${user.username}" }
                    p { code("password") { +password } }
                    p { +"Send it to them. They can pick their own under the account menu once they have logged in." }
                    p { +"It isn't shown again." }
                    a("/") { +"Back to users" }
                }
            }
        }

        get("/users/{id}/delete") {
            val user = users.findById(call.userId()) ?: return@get call.notFound()
            call.respondHtml {
                page("Delete user") {
                    h1 { +"Delete ${user.username}?" }
                    p { +"This deletes their account, drink history and friendships. It can't be undone." }
                    form("/users/${user.id}/delete", method = FormMethod.post) {
                        button(type = ButtonType.submit, classes = "danger") { +"Delete ${user.username}" }
                        +" "
                        a("/") { +"Cancel" }
                    }
                }
            }
        }

        post("/users/{id}/delete") {
            val user = users.findById(call.userId()) ?: return@post call.notFound()
            users.deleteUser(user.id)
            notifications.disconnect(user.id)
            log.info("{} deleted the user {}", call.adminLogin, user.username)
            call.respondRedirect("/?deleted=${user.username}")
        }
    }
}

class TailscaleAccessConfig {
    var allowedLogins: Set<String> = emptySet()
}

/**
 * Lets in only people in the tailnet named in [TailscaleAccessConfig.allowedLogins]. It also refuses changes
 * (POST) that don't come from the panel's own pages: the browser of someone signed in would otherwise send
 * a form from any other site too, since being in the tailnet is all it takes.
 */
private val TailscaleAccess = createApplicationPlugin("TailscaleAccess", ::TailscaleAccessConfig) {
    val allowed = pluginConfig.allowedLogins
    onCall { call ->
        with(call.response) {
            header("Cache-Control", "no-store")
            header("X-Frame-Options", "DENY")
            header("Referrer-Policy", "no-referrer")
            header(
                "Content-Security-Policy",
                "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'",
            )
        }
        val login = call.request.headers[TAILSCALE_LOGIN]
        val refusal = when {
            call.request.headers[TAILSCALE_FUNNEL] != null -> "The admin panel is not available from the internet."
            login == null -> "Open the admin panel through Tailscale (tailscale serve)."
            login !in allowed -> "$login may not use the admin panel."
            call.request.local.method == HttpMethod.Post && call.request.headers["Sec-Fetch-Site"] != "same-origin" ->
                "Changes can only be made from the admin panel's own pages."
            else -> null
        }
        if (refusal != null) {
            log.warn("Refused {} {} for {}: {}", call.request.local.method.value, call.request.local.uri, login, refusal)
            call.respondHtml(HttpStatusCode.Forbidden) {
                page("Not allowed") {
                    h1 { +"Not allowed" }
                    p { +refusal }
                }
            }
        }
    }
}

private val ApplicationCall.adminLogin: String get() = request.headers[TAILSCALE_LOGIN].orEmpty()

private fun RoutingCall.userId(): Int = parameters["id"]?.toIntOrNull() ?: -1

private suspend fun RoutingCall.notFound() {
    respondHtml(HttpStatusCode.NotFound) {
        page("Not found") {
            h1 { +"No such user" }
            a("/") { +"Back to users" }
        }
    }
}

/** A cell that names its column, which narrow screens show in front of it since they hide the header row. */
private fun TR.labelled(label: String, value: String) {
    td {
        attributes["data-label"] = label
        +value
    }
}

private fun formatTime(epochMillis: Long): String = timeFormat.format(Instant.ofEpochMilli(epochMillis))

private fun FlowContent.stat(value: Int, label: String) {
    div("stat") {
        span("value") { +value.toString() }
        span("label") { +label }
    }
}

private fun kotlinx.html.HTML.page(title: String, content: BODY.() -> Unit) {
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title { +title }
        style {
            unsafe {
                +"""
                body { font: 16px -apple-system, system-ui, sans-serif; margin: 0 auto; padding: 16px; max-width: 960px; color: #1c1b1f; }
                h1 { font-size: 24px; }
                .stats { display: flex; flex-wrap: wrap; gap: 12px; }
                .stat { border: 1px solid #ddd; border-radius: 8px; padding: 12px 16px; min-width: 120px; }
                .stat .value { display: block; font-size: 28px; font-weight: 600; }
                .stat .label { color: #666; font-size: 14px; }
                table { border-collapse: collapse; width: 100%; }
                th, td { text-align: left; padding: 8px; border-bottom: 1px solid #eee; white-space: nowrap; }
                @media (max-width: 640px) {
                    thead { display: none; }
                    tr { display: block; padding: 12px 0; border-bottom: 1px solid #eee; }
                    td { display: block; padding: 2px 0; border: none; }
                    td[data-label]::before { content: attr(data-label) ": "; color: #666; }
                    td.actions { padding-top: 8px; }
                }
                a { color: #6200ee; }
                .danger { color: #b00020; }
                button { font: inherit; padding: 8px 16px; border-radius: 6px; border: 1px solid #6200ee; background: #6200ee; color: white; }
                button.danger { background: #b00020; border-color: #b00020; color: white; }
                .notice { background: #e8f5e9; padding: 8px 12px; border-radius: 6px; }
                .password { font-size: 24px; background: #f3f0f7; padding: 4px 8px; border-radius: 4px; }
                .whoami { color: #666; font-size: 14px; margin-top: 24px; }
                """.trimIndent()
            }
        }
    }
    body { content() }
}

/** Runs the admin panel next to the app, on [AdminConfig.host]. It starts and stops with the app. */
fun Application.startAdminPanel(
    config: AdminConfig,
    admin: AdminService,
    users: UserService,
    notifications: NotificationService,
) {
    val panel = embeddedServer(Netty, port = config.port, host = config.host) {
        adminModule(config, admin, users, notifications)
    }
    monitor.subscribe(ApplicationStarted) { panel.start() }
    monitor.subscribe(ApplicationStopping) { panel.stop() }
}
