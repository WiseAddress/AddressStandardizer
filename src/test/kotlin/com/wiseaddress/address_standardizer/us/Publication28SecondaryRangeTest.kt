package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Publication28SecondaryRangeTest {
    private val parser = USStandardizer()

    @Test
    fun `bounded alphabetic highway unit takes precedence over a same-spelled suffix`() {
        val expected = USStandardizedAddress("123", "", "HIGHWAY 1", "", "", "KEY", "AB", "BOSTON", "MA", "02108")
        assertEquals(expected, parser.standardize("123 HIGHWAY 1 KEY AB, BOSTON MA 02108"))
        assertEquals(expected, parser.standardize(expected.formatLines().joinToString("\n")))
    }

    // Appendix E12 includes pure-alpha receptacles and multiple-alpha apartment ranges.
    // https://pe.usps.com/text/pub28/28ape_003.htm
    @TestFactory
    fun `bounded secondary identifiers preserve all supplied alpha positions`() =
        listOf("APT", "STE").flatMap { unit ->
            listOf("AB", "AAA", "15AB", "101AAA", "A1A", "AB90").flatMap { range ->
                listOf(", ", "\n").map { boundary ->
                    dynamicTest("$unit $range ${if (boundary == "\n") "newline" else "comma"}") {
                        val expected = USStandardizedAddress("123", "", "MAIN", "ST", "", unit, range, "BOSTON", "MA", "02108")
                        assertEquals(expected, parser.standardize("123 MAIN ST $unit $range${boundary}BOSTON MA 02108"))
                        assertEquals(expected, parser.standardize(expected.formatLines().joinToString("\n")))
                    }
                }
            }
        }

    @TestFactory
    fun `alphabetic ranges do not make words inside a street or city into units`() = listOf(
        "123 FRONT STREET, BOSTON MA 02108" to USStandardizedAddress("123", "", "FRONT", "ST", "", "", "", "BOSTON", "MA", "02108"),
        "123 N FRONT STREET BOSTON MA 02108" to USStandardizedAddress("123", "N", "FRONT", "ST", "", "", "", "BOSTON", "MA", "02108"),
        "123 MAIN ST, FRONT ROYAL VA 22630" to USStandardizedAddress("123", "", "MAIN", "ST", "", "", "", "FRONT ROYAL", "VA", "22630"),
        "123 HIGHWAY 70 KEY WEST FL 33040" to USStandardizedAddress("123", "", "HIGHWAY 70", "", "", "", "", "KEY WEST", "FL", "33040"),
    ).map { (input, expected) -> dynamicTest(input) { assertEquals(expected, parser.standardize(input)) } }

    @TestFactory
    fun `reversed mailbox or repeated physical units cannot become alphabetic city text`() = listOf(
        "123 MAIN ST PMB 4 APT AB BOSTON MA 02108",
        "123 MAIN ST APT 4 STE AAA BOSTON MA 02108",
    ).map { input -> dynamicTest(input) { assertFailsWith<InvalidAddressException> { parser.standardize(input) } } }
}
