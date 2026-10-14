package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Publication28LayoutTest {
    private val parser = USStandardizer()

    @TestFactory
    fun `state matching cannot consume a city across an explicit separator`() = listOf(
        Triple("WEST, VIRGINIA 20101", "WEST", "VA"),
        Triple("WEST\nVIRGINIA 20101", "WEST", "VA"),
        Triple("WEST, VA 20101", "WEST", "VA"),
        Triple("CHARLESTON WEST VIRGINIA 25301", "CHARLESTON", "WV"),
        Triple("CHARLESTON, WEST VIRGINIA 25301", "CHARLESTON", "WV"),
    ).map { (lastLine, city, state) -> dynamicTest(lastLine) {
        val actual = parser.standardize("123 MAIN ST, $lastLine")
        assertEquals(city, actual.city)
        assertEquals(state, actual.state)
    } }

    // Publication 28 §213.3 permits secondary information above the delivery line.
    @TestFactory
    fun `secondary lines above or below a street retain their components`() = listOf(
        "APT 4", "APARTMENT 4", "STE B", "SUITE B", "FL 12", "BLDG 2A", "RM 104",
        "DEPT 9", "UNIT 2", "# 42", "BSMT", "FRONT", "LBBY", "LOWR", "OFC", "PH", "REAR", "SIDE", "UPPR",
    ).flatMap { unit ->
        listOf("\n", "\r\n", ", ").flatMap { separator ->
            listOf(
                "$unit${separator}123 Main Street${separator}Boston MA 02108",
                "123 Main Street${separator}$unit${separator}Boston MA 02108",
            ).map { input -> dynamicTest(input.replace("\n", " / ")) {
                val expected = parser.standardize("123 Main Street $unit, Boston MA 02108")
                val actual = parser.standardize(input)
                assertEquals(expected, actual)
                assertEquals(expected, parser.standardize(actual.formatLines().joinToString("\n")))
            } }
        }
    }

    @TestFactory
    fun `secondary information above a delivery survives when the city is omitted`() = listOf(
        "APT 4\n123 MAIN ST\nMA 02108" to USStandardizedAddress(
            "123", "", "MAIN", "ST", "", "APT", "4", "", "MA", "02108",
        ),
        "PMB 234\nRR 1 BOX 12\nVA 22071" to USStandardizedAddress(
            "", "", "", "", "", "", "", "", "VA", "22071",
            USAddressType.RURAL_ROUTE, "1", "12", privateMailbox = "234",
        ),
    ).map { (input, expected) -> dynamicTest(input.replace("\n", " / ")) {
        val actual = parser.standardize(input)
        assertEquals(expected, actual)
        assertEquals(expected, parser.standardize(actual.formatLines().joinToString("\n")))
    } }

    // Publication 28 §§291–295: keep CALLE and the urbanization as supplied.
    @TestFactory
    fun `Puerto Rico urbanization and street words survive normalization`() = listOf(
        Triple("URB LAS GLADIOLAS\n150 CALLE A\nSAN JUAN PR 00926-3232", "LAS GLADIOLAS", "150 CALLE A"),
        Triple("Urbanization Las Flores, 12 Calle 3, San Juan Puerto Rico 00901", "LAS FLORES", "12 CALLE 3"),
        Triple("Urbanizacion Las Flores\n12 Calle 3\nSan Juan PR 00901", "LAS FLORES", "12 CALLE 3"),
        Triple("URB JARDINES\nA17 CALLE NORTE\nSAN JUAN PR 00901", "JARDINES", "A17 CALLE NORTE"),
        Triple("URB LAS FLORES\n123 AVENIDA OESTE\nSAN JUAN PR 00901", "LAS FLORES", "123 AVENIDA OESTE"),
        Triple("URB LAS FLORES\n12 CALLE A APT 4\nSAN JUAN PR 00901", "LAS FLORES", "12 CALLE A APT 4"),
        Triple("URB LAS FLORES\nAPT 4\n12 CALLE A\nSAN JUAN PR 00901", "LAS FLORES", "12 CALLE A APT 4"),
    ).map { (input, urbanization, delivery) -> dynamicTest(input.replace("\n", " / ")) {
        val actual = parser.standardize(input)
        assertEquals(urbanization, actual.urbanization)
        assertEquals(delivery, actual.formatLines()[1])
        assertEquals("SAN JUAN", actual.city)
        assertEquals("PR", actual.state)
        assertEquals("", actual.preDirection)
        assertEquals("", actual.postDirection)
        assertEquals(actual, parser.standardize(actual.formatLines().joinToString("\n")))
    } }

    // §222 allows punctuation; §231 requires meaningful component spacing.
    @TestFactory
    fun `explicit locality boundaries preserve suffixlike and directional city names`() = listOf(
        "WEST SPRINGFIELD", "NORTH POLE", "LAKE CITY", "KEY WEST", "FRONT ROYAL", "SAINT PAUL", "ST LOUIS", "O'FALLON", "WINSTON-SALEM",
    ).flatMap { city -> listOf(
        "123 Main Street, $city, IL 62704",
        "123 Main Street\n$city IL 62704",
        "123 Main Street, Apt 4, $city, Illinois 62704",
    ).map { input -> dynamicTest(input) {
        val actual = parser.standardize(input)
        assertEquals(city, actual.city)
        assertEquals("MAIN", actual.streetName)
        assertEquals("ST", actual.suffix)
        assertEquals("", actual.postDirection)
        assertEquals(actual, parser.standardize(actual.formatLines().joinToString("\n")))
    } } }

    @TestFactory
    fun `malformed and unsupported multiline forms fail instead of absorbing lines`() = listOf(
        "APT\n123 MAIN ST\nBOSTON MA 02108",
        "APT 4\nBOSTON MA 02108",
        "APT 4\n123 MAIN ST APT 5\nBOSTON MA 02108",
        "URB\n12 CALLE A\nSAN JUAN PR 00901",
        "URB LAS FLORES\n12 MAIN ST\nBOSTON MA 02108",
        "URB LAS FLORES\nURB LAS PALMAS\n12 CALLE A\nSAN JUAN PR 00901",
        "URB LAS FLORES\nPO BOX 12\nSAN JUAN PR 00901",
        "URB LAS FLORES 12 CALLE A SAN JUAN PR 00901",
        "123 MAIN ST\nACME INC\nBOSTON MA 02108",
        "123 MAIN ST\nBOSTON\nAPT 4 MA 02108",
        "123 MAIN ST\nBOSTON\nUNIT 4\nMA 02108",
        "URB LAS FLORES\n12 CALLE A\nSAN JUAN\nAPT 4 PR 00901",
        "PO BOX 12\nUNIT 1 MA 02108",
    ).map { input -> dynamicTest(input.replace("\n", " / ")) {
        assertFailsWith<InvalidAddressException> { parser.standardize(input) }
    } }
}
