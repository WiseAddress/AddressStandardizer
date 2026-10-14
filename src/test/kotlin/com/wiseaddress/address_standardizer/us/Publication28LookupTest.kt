package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import com.wiseaddress.address_standardizer.model.ModelFactory
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Publication28LookupTest {
    private val parser = USStandardizer()
    private val model = ModelFactory.load()

    @TestFactory
    fun `C1 street suffix forms preserve components and canonical output`() = fixture("street-suffixes.tsv").map { (alias, suffix) ->
        dynamicTest("C1 $alias -> $suffix") {
            assertEquals(suffix, model.street_abbreviations[alias])
            val expected = USStandardizedAddress("123", "", "MAIN", suffix, "", "", "", "SPRINGFIELD", "IL", "62704")
            assertEquals(expected, parser.standardize("123 Main $alias, Springfield IL 62704"))
            assertEquals(expected, parser.standardize("123 main ${alias.lowercase(Locale.ROOT)}.\nSpringfield IL 62704"))
            assertEquals(expected, parser.standardize(expected.formatLines().joinToString("\n")))
        }
    }

    @TestFactory
    fun `Appendix B state and possession names normalize without changing locality`() = fixture("states.tsv").flatMap { (name, code) ->
        listOf(name, code).map { input ->
            dynamicTest("Appendix B $input -> $code") {
                assertEquals(code, model.states[input])
                val expected = USStandardizedAddress("123", "", "MAIN", "ST", "", "", "", "SPRINGFIELD", code, "12345")
                assertEquals(expected, parser.standardize("123 Main Street, Springfield $input 12345"))
            }
        }
    }

    @TestFactory
    fun `C2 units accept full names and approved abbreviations`() = fixture("secondary-units.tsv").flatMap { (name, code) ->
        listOf(name, code).distinct().map { input ->
            dynamicTest("C2 $input -> $code") {
                assertEquals(code, model.secondary_units[input])
                val expected = USStandardizedAddress("123", "", "MAIN", "ST", "", code, "7", "SPRINGFIELD", "IL", "62704")
                assertEquals(expected, parser.standardize("123 Main Street $input 7, Springfield IL 62704"))
                assertEquals(expected, parser.standardize("123 Main Street $input #7, Springfield IL 62704"))
                assertEquals(expected, parser.standardize(expected.formatLines().joinToString("\n")))
            }
        }
    }

    @TestFactory
    fun `C2 only starred units may omit a secondary range`() = fixture("secondary-units.tsv").map { (name, code, range) ->
        dynamicTest("C2 $code range $range") {
            for (input in listOf(name, code).distinct()) {
                val address = "123 Main Street $input, Springfield IL 62704"
                if (range == "optional") {
                    val expected = USStandardizedAddress("123", "", "MAIN", "ST", "", code, "", "SPRINGFIELD", "IL", "62704")
                    assertEquals(expected, parser.standardize(address))
                } else if (input !in model.street_abbreviations) {
                    // KEY and TRAILER are also street suffixes; without a range their role is ambiguous.
                    assertFailsWith<InvalidAddressException> { parser.standardize(address) }
                }
                assertFailsWith<InvalidAddressException> {
                    parser.standardize("123 Main Street $input #, Springfield IL 62704")
                }
            }
        }
    }

    @Test
    fun `C2 preserves the common hangar spelling alongside the published hanger spelling`() {
        assertEquals("HNGR", parser.standardize("123 Main Street Hangar 7, Springfield IL 62704").secondaryIdentifier)
        assertEquals(
            fixture("secondary-units.tsv").filter { it[2] == "optional" }.map { it[1] }.toSet(),
            model.secondary_range_unneeded.toSet(),
        )
    }

    @TestFactory
    fun `Appendix B directionals normalize before and after a street name`() = listOf(
        "NORTH" to "N", "EAST" to "E", "SOUTH" to "S", "WEST" to "W",
        "NORTHEAST" to "NE", "SOUTHEAST" to "SE", "NORTHWEST" to "NW", "SOUTHWEST" to "SW",
    ).flatMap { (name, code) ->
        listOf(name, code).map { input ->
            dynamicTest("Appendix B directional $input -> $code") {
                assertEquals(code, model.directions[input])
                assertEquals(
                    USStandardizedAddress("123", code, "MAIN", "ST", code, "", "", "SPRINGFIELD", "IL", "62704"),
                    parser.standardize("123 $input Main Street $input, Springfield IL 62704"),
                )
            }
        }
    }

    @TestFactory
    fun `Appendix B military state codes remain coupled to military localities`() = listOf("AA", "AE", "AP").flatMap { state ->
        listOf("APO", "FPO", "DPO").map { city ->
            dynamicTest("Appendix B military $city $state") {
                val parsed = parser.standardize("PO BOX 7, $city $state 09012")
                assertEquals(city, parsed.city)
                assertEquals(state, parsed.state)
                assertEquals(USAddressType.PO_BOX, parsed.addressType)
                assertFailsWith<InvalidAddressException> { parser.standardize("PO BOX 7, SPRINGFIELD $state 09012") }
            }
        }
    }

    @TestFactory
    fun `Appendix F highway examples retain route names and suffix modifiers`() = fixture("highways.tsv").map { (input, expected) ->
        dynamicTest("Appendix F $input -> $expected") {
            val parsed = parser.standardize("123 $input, Springfield IL 62704")
            assertEquals("123 $expected SPRINGFIELD IL 62704", parsed.format())
            assertEquals("123", parsed.primaryRange)
            assertEquals("SPRINGFIELD", parsed.city)
            assertEquals("IL", parsed.state)
            assertEquals("62704", parsed.postalCode)
            assertEquals(parsed, parser.standardize(parsed.formatLines().joinToString("\n")))
        }
    }

    @TestFactory
    fun `C1 plural primary names stay plural before another suffix`() = listOf("PARKS", "PARKWAYS", "SPURS", "WALKS").map { primary ->
        dynamicTest("C1 primary name $primary DRIVE") {
            val parsed = parser.standardize("123 Main $primary Drive, Springfield IL 62704")
            assertEquals("MAIN $primary", parsed.streetName)
            assertEquals("DR", parsed.suffix)
            assertEquals(parsed, parser.standardize(parsed.formatLines().joinToString("\n")))
        }
    }

    private fun fixture(name: String): List<List<String>> = checkNotNull(
        javaClass.getResourceAsStream("/com/wiseaddress/address_standardizer/us/publication28/$name"),
    ).bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { it.split('\t') }.toList()
    }
}
