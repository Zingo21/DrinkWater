package org.erbeenjoyers.drinkwater

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import config.AppConfig
import database.DatabaseFactory
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import services.UserService
import java.io.PrintStream
import java.security.SecureRandom

private const val USAGE = """Usage: server <command>

Commands:
  reset-password <username>   Give a locked-out user a new, random password and log them out everywhere.

Run through Gradle with: ./gradlew :server:run --args="reset-password <username>"
"""

/**
 * Maintenance commands for whoever runs the server. They work on the database directly, so they
 * can be run while the server is up. Returns the process exit code.
 */
fun runCommand(
    args: List<String>,
    databaseUrl: String = AppConfig.databaseUrlFromEnvironment(),
    out: PrintStream = System.out,
): Int = when (args.firstOrNull()) {
    "reset-password" -> {
        val username = args.getOrNull(1)
        if (username == null || args.size > 2) {
            out.print(USAGE)
            2
        } else {
            resetPassword(username, databaseUrl, out)
        }
    }
    else -> {
        out.print(USAGE)
        if (args.firstOrNull() in listOf("help", "--help", "-h")) 0 else 2
    }
}

private fun resetPassword(username: String, databaseUrl: String, out: PrintStream): Int {
    // The server logs everything down to TRACE, which would bury the one line that matters here.
    (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? Logger)?.level = Level.WARN
    val users = UserService(DatabaseFactory.init(databaseUrl))
    val password = temporaryPassword()
    val found = runBlocking { users.resetPassword(username, password) }
    if (!found) {
        out.println("No user called \"$username\"")
        return 1
    }
    out.println("New password for ${username.trim().lowercase()}: $password")
    out.println("They are logged out on all devices. Ask them to log in and change it under the account menu.")
    return 0
}

/** Easy to read out or type: no 0/o, 1/l/i, grouped like `abcd-efgh-jkmn`. */
internal fun temporaryPassword(random: SecureRandom = SecureRandom()): String {
    val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
    return (1..3).joinToString("-") { (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("") }
}
