package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Publication28DualAddressTest {
    private val parser = USStandardizer()

    // Publication 28 §215: the delivery nearest the locality wins; remove the alternate from output.
    // https://pe.usps.com/text/pub28/28c2_005.htm
    @Test
    fun `215 published dual address example outputs the intended PO box`() {
        val input = "1201 BROAD ST E\nPO BOX 1001\nFALLS CHURCH VA 22041-1001"
        val expected = box("1001").copy(city = "FALLS CHURCH", state = "VA", postalCode = "22041-1001")
        assertDelivery(input, expected)
    }

    @TestFactory
    fun `215 complete alternate delivery lines are accepted`() = listOf(
        "123 MAIN ST\n456 OAK ST\nBOSTON MA 02108" to street("456", "OAK", "ST"),
        "123 MAIN ST\nPO BOX 12\nBOSTON MA 02108" to box("12"),
    ).map { (input, expected) -> dynamicTest(input.replace("\n", " / ")) { assertDelivery(input, expected) } }

    @TestFactory
    fun `215 last delivery controls address type and fields in either order`() = listOf(
        Triple("123 MAIN STREET", "PO BOX 7", box("7")),
        Triple("PO BOX 7", "456 OAK ROAD", street("456", "OAK", "RD")),
        Triple("123 MAIN STREET", "RR 02 BOX 003", route(USAddressType.RURAL_ROUTE, "2", "003")),
        Triple("RR 02 BOX 003", "456 OAK ROAD", street("456", "OAK", "RD")),
        Triple("123 MAIN STREET", "HC 068A BOX 45-1", route(USAddressType.HIGHWAY_CONTRACT, "68A", "45-1")),
        Triple("HC 068A BOX 45-1", "456 OAK ROAD", street("456", "OAK", "RD")),
        Triple("12 OLD BROADWAY", "PO BOX 007", box("007")),
        Triple("PO BOX 7", "12 OLD BROADWAY", street("12", "OLD BROADWAY", "")),
        Triple("123 N MAIN STREET S APT 9", "PO BOX 7", box("7")),
        Triple("P.O. BOX 7", "456 OAK ROAD SUITE B", street("456", "OAK", "RD", "STE", "B")),
        Triple("123 MAIN ST APT 9", "456 OAK ROAD APT 4", street("456", "OAK", "RD", "APT", "4")),
        Triple("123 MAIN ST", "456 EAST PINE AVENUE NORTH", street("456", "PINE", "AVE").copy(preDirection = "E", postDirection = "N")),
        Triple("POST OFFICE BOX 7", "N6W23001 BLUEMOUND ROAD", street("N6W23001", "BLUEMOUND", "RD")),
        Triple("RR 2 BOX 7", "12 1/2 MAIN STREET", street("12 1/2", "MAIN", "ST")),
        Triple("456 OAK RD", "123 MAIN AVE DRIVE", street("123", "MAIN AVENUE", "DR")),
        Triple("PO BOX 7", "123 BROADWAY PMB 42", street("123", "BROADWAY", "").copy(privateMailbox = "42")),
    ).flatMap { (alternate, intended, expected) ->
        listOf("\n", "\r\n").map { separator ->
            val input = listOf(alternate, intended, "BOSTON MA 02108").joinToString(separator)
            dynamicTest("215 ${input.replace(separator, " / ")}") { assertDelivery(input, expected) }
        }
    }

    // §213.3 permits secondary information immediately above its delivery line.
    @TestFactory
    fun `213 and 215 secondary information stays with its own delivery address`() = listOf(
        "APT 9\n123 MAIN ST\nPO BOX 7\nBOSTON MA 02108" to box("7"),
        "PO BOX 7\nAPT 4\n456 OAK RD\nBOSTON MA 02108" to street("456", "OAK", "RD", "APT", "4"),
        "APT 9\n123 MAIN ST\nAPT 4\n456 OAK RD\nBOSTON MA 02108" to street("456", "OAK", "RD", "APT", "4"),
        "123 MAIN ST\nAPT 4\n456 OAK RD\nBOSTON MA 02108" to street("456", "OAK", "RD", "APT", "4"),
        "PO BOX 7\nREAR\n456 OAK RD\nBOSTON MA 02108" to street("456", "OAK", "RD", "REAR", ""),
        "REAR\n123 MAIN ST\nPO BOX 7\nBOSTON MA 02108" to box("7"),
    ).map { (input, expected) -> dynamicTest(input.replace("\n", " / ")) { assertDelivery(input, expected) } }

    @TestFactory
    fun `215 locality belongs only to the selected delivery and supplied ZIP remains unchanged`() = listOf(
        "BOSTON MASSACHUSETTS 02108" to box("7"),
        "BOSTON MA 02108-0042" to box("7").copy(postalCode = "02108-0042"),
        "KEY WEST FL 33040" to box("7").copy(city = "KEY WEST", state = "FL", postalCode = "33040"),
        "FRONT ROYAL VA 22630" to box("7").copy(city = "FRONT ROYAL", state = "VA", postalCode = "22630"),
        "WEST, VIRGINIA 20101" to box("7").copy(city = "WEST", state = "VA", postalCode = "20101"),
    ).map { (locality, expected) ->
        dynamicTest("215 locality $locality") {
            assertDelivery("123 MAIN ST\nPO BOX 7\n$locality", expected)
        }
    }

    @TestFactory
    fun `215 military UNIT lines remain deliveries when paired with a physical street`() = listOf(
        "123 MAIN ST\nUNIT 8400 BOX 12\nDPO AE 09498" to USStandardizedAddress(
            "", "", "", "", "", "", "", "DPO", "AE", "09498",
            USAddressType.MILITARY, "8400", "12", "UNIT",
        ),
        "UNIT 8400 BOX 12\n123 MAIN ST\nDPO AE 09498" to street("123", "MAIN", "ST").copy(
            city = "DPO", state = "AE", postalCode = "09498",
        ),
    ).map { (input, expected) -> dynamicTest(input.replace("\n", " / ")) { assertDelivery(input, expected) } }

    @TestFactory
    fun `malformed and unsupported dual layouts fail without discarding unknown data`() = listOf(
        "123\nPO BOX 7\nBOSTON MA 02108",
        "123 MAIN ST APT\nPO BOX 7\nBOSTON MA 02108",
        "PO BOX\n456 OAK RD\nBOSTON MA 02108",
        "PO BOX 12A\n456 OAK RD\nBOSTON MA 02108",
        "RR X BOX 7\n456 OAK RD\nBOSTON MA 02108",
        "HC 2\n456 OAK RD\nBOSTON MA 02108",
        "123 MAIN ST\nPO BOX\nBOSTON MA 02108",
        "PO BOX 7\n456 OAK RD APT\nBOSTON MA 02108",
        "123 MAIN ST PO BOX 7\nBOSTON MA 02108",
        "PO BOX 7 456 OAK RD\nBOSTON MA 02108",
        "123 MAIN ST\nPO BOX 7 BOSTON MA 02108",
        "PO BOX 7\n456 OAK RD BOSTON MA 02108",
        "123 MAIN ST\nRR 2 BOX 7 BOSTON MA 02108",
        "123 MAIN ST\nBOSTON MA 02108\nPO BOX 7\nBOSTON MA 02108",
        "PO BOX 7\nAPT 4\nBOSTON MA 02108",
        "APT 4\nPO BOX 7\n456 OAK RD\nBOSTON MA 02108",
        "123 MAIN ST\nPO BOX 7\nAPT 4\nBOSTON MA 02108",
    ).map { input -> dynamicTest(input.replace("\n", " / ")) {
        assertFailsWith<InvalidAddressException> { parser.standardize(input) }
    } }

    @Test
    fun `dual delivery support does not extend to three competing delivery addresses`() {
        // §215 describes one intended delivery and one alternate; larger groups remain unsupported.
        assertFailsWith<InvalidAddressException> {
            parser.standardize("123 MAIN ST\n456 OAK RD\nPO BOX 7\nBOSTON MA 02108")
        }
    }

    @TestFactory
    fun `215 does not treat unsupported recipient or company lines as discardable alternate addresses`() = listOf(
        "JANE DOE\nPO BOX 7\nBOSTON MA 02108",
        "ACME INC\n456 OAK RD\nBOSTON MA 02108",
        "ATTN JANE DOE\nPO BOX 7\nBOSTON MA 02108",
        "JANE DOE\n123 MAIN ST\nPO BOX 7\nBOSTON MA 02108",
        "123 MAIN ST\nACME INC\nPO BOX 7\nBOSTON MA 02108",
        "123 MAIN ST\nPO BOX 7\nACME INC\nBOSTON MA 02108",
    ).map { input -> dynamicTest(input.replace("\n", " / ")) {
        assertFailsWith<InvalidAddressException> { parser.standardize(input) }
    } }

    private fun assertDelivery(input: String, expected: USStandardizedAddress) {
        val actual = parser.standardize(input)
        assertEquals(expected, actual)
        assertEquals(expected.formatLines(), actual.formatLines())
        assertEquals(expected, parser.standardize(actual.formatLines().joinToString("\n")))
    }

    private fun street(number: String, name: String, suffix: String, unit: String = "", range: String = "") =
        USStandardizedAddress(number, "", name, suffix, "", unit, range, "BOSTON", "MA", "02108")

    private fun box(number: String) =
        USStandardizedAddress("", "", "", "", "", "", "", "BOSTON", "MA", "02108", USAddressType.PO_BOX, boxNumber = number)

    private fun route(type: USAddressType, number: String, box: String) =
        USStandardizedAddress("", "", "", "", "", "", "", "BOSTON", "MA", "02108", type, number, box)
}
