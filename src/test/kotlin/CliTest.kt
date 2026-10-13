import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliTest {
    @Test
    fun `quoted and separate arguments produce canonical output`() {
        val address = "123 Main Street Springfield IL 62704"
        for (args in listOf(arrayOf(address), address.split(" ").toTypedArray())) {
            val result = invoke(*args)
            assertEquals(0, result.exitCode)
            assertEquals("123 MAIN ST SPRINGFIELD IL 62704", result.output.trim())
            assertEquals("", result.error)
        }
    }

    @Test
    fun `help is successful and does not write errors`() {
        for (option in listOf("--help", "-h")) {
            val result = invoke(option)
            assertEquals(0, result.exitCode)
            assertTrue(result.output.startsWith("Usage:"))
            assertEquals("", result.error)
        }
    }

    @Test
    fun `missing arguments and unknown options report usage errors`() {
        for (args in listOf(emptyArray(), arrayOf("--unknown"))) {
            val result = invoke(*args)
            assertEquals(2, result.exitCode)
            assertEquals("", result.output)
            assertTrue(result.error.isNotBlank())
        }
    }

    @Test
    fun `invalid address reports a concise error without output or stack trace`() {
        val result = invoke("not an address")
        assertEquals(2, result.exitCode)
        assertEquals("", result.output)
        assertTrue(result.error.startsWith("Invalid address:"))
        assertEquals(1, result.error.trim().lines().size)
    }

    private fun invoke(vararg args: String): Result {
        val output = ByteArrayOutputStream()
        val error = ByteArrayOutputStream()
        val exitCode = PrintStream(output, true, Charsets.UTF_8).use { out ->
            PrintStream(error, true, Charsets.UTF_8).use { err ->
                runCli(arrayOf(*args), out, err)
            }
        }
        return Result(exitCode, output.toString(Charsets.UTF_8), error.toString(Charsets.UTF_8))
    }

    private data class Result(val exitCode: Int, val output: String, val error: String)
}
