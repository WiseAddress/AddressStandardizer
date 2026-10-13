import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import com.wiseaddress.address_standardizer.exception.InvalidModelException
import com.wiseaddress.address_standardizer.us.USStandardizer
import java.io.PrintStream
import kotlin.system.exitProcess

private const val USAGE = """Usage: address-standardizer <US address>

Normalizes a supported US address into one canonical line.
Quote the address to preserve commas or delivery/locality line breaks.

Example: address-standardizer "123 Main Street, Springfield IL 62704"

Options:
  -h, --help  Show this help

Exit codes: 0 success, 2 invalid input, 1 model unavailable.
"""

fun main(args: Array<String>) {
    exitProcess(runCli(args, System.out, System.err))
}

internal fun runCli(args: Array<String>, output: PrintStream, error: PrintStream): Int {
    if (args.size == 1 && (args[0] == "-h" || args[0] == "--help")) {
        output.print(USAGE)
        return 0
    }
    if (args.isEmpty()) {
        error.print(USAGE)
        return 2
    }
    if (args[0].startsWith("-")) {
        error.println("Unknown option: ${args[0]}. Use --help for usage.")
        return 2
    }

    return try {
        output.println(USStandardizer().standardize(args.joinToString(" ")).format())
        0
    } catch (exception: InvalidAddressException) {
        error.println("Invalid address: ${exception.message}")
        2
    } catch (exception: InvalidModelException) {
        error.println("Cannot load address data: ${exception.message}")
        1
    }
}
