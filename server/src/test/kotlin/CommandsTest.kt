import kotlinx.coroutines.runBlocking
import org.erbeenjoyers.drinkwater.runCommand
import org.erbeenjoyers.drinkwater.temporaryPassword
import database.DatabaseFactory
import services.UserService
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandsTest {
    private val dbFile = File.createTempFile("drinkwater-commands", ".db")
    private val databaseUrl = "jdbc:sqlite:${dbFile.absolutePath}"

    @AfterTest
    fun deleteDatabase() {
        dbFile.delete()
    }

    private fun run(vararg args: String): Pair<Int, String> {
        val output = ByteArrayOutputStream()
        val code = runCommand(args.toList(), databaseUrl, PrintStream(output, true))
        return code to output.toString()
    }

    @Test
    fun `reset-password gives the user a new password that works`() = runBlocking {
        val users = UserService(DatabaseFactory.init(databaseUrl))
        val user = users.register("anna", "forgotten-password")
        val versionBefore = users.tokenVersion(user.id)!!

        val (code, output) = run("reset-password", "Anna")
        assertEquals(0, code)
        val password = Regex("New password for anna: (\\S+)").find(output)!!.groupValues[1]

        assertEquals(user, users.authenticate("anna", password))
        // Logged out everywhere.
        assertEquals(versionBefore + 1, users.tokenVersion(user.id))
    }

    @Test
    fun `reset-password reports an unknown user`() {
        val (code, output) = run("reset-password", "nobody")
        assertEquals(1, code)
        assertTrue("No user called" in output)
    }

    @Test
    fun `wrong usage prints help`() {
        assertEquals(2, run("reset-password").first)
        assertEquals(2, run("frobnicate").first)
        assertEquals(0, run("help").first)
    }

    @Test
    fun `temporary passwords are long enough and easy to read`() {
        repeat(20) {
            val password = temporaryPassword()
            assertTrue(Regex("^[a-z2-9]{4}-[a-z2-9]{4}-[a-z2-9]{4}$").matches(password), password)
            assertTrue(password.none { it in "01lio" }, password)
        }
    }
}
