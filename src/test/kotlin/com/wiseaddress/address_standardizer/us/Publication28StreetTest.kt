package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Synthetic delivery lines exercise Pub 28 grammar, without claiming ZIP+4 deliverability. */
class Publication28StreetTest {
    private val parser = USStandardizer()

    private data class Case(
        val input: String,
        val number: String = "123",
        val pre: String = "",
        val name: String = "MAIN",
        val suffix: String = "ST",
        val post: String = "",
        val unit: String = "",
        val range: String = "",
        val unbounded: Boolean = true,
    ) {
        val expected get() = USStandardizedAddress(number, pre, name, suffix, post, unit, range, "AUSTIN", "TX", "78701")
    }

    // https://pe.usps.com/text/pub28/28c2_014.htm (§233)
    private val directions = listOf(
        "North" to "N", "South" to "S", "East" to "E", "West" to "W",
        "Northeast" to "NE", "Northwest" to "NW", "Southeast" to "SE", "Southwest" to "SW",
    )

    private val cases = buildList {
        for ((full, short) in directions) {
            for (alias in listOf(full, short, "$short.")) {
                add(Case("123 $alias Main Street", pre = short))
                add(Case("123 Main Street $alias", post = short, unbounded = alias != full))
            }
            add(Case("123 $full Street", name = full.uppercase()))
            add(Case("123 Bay $full Drive", name = "BAY ${full.uppercase()}", suffix = "DR"))
        }
        for ((first, second, expected) in listOf(
            Triple("North", "East", "NE"), Triple("North", "West", "NW"),
            Triple("South", "East", "SE"), Triple("South", "West", "SW"),
        )) {
            add(Case("123 $first $second Main Street", pre = expected))
            add(Case("123 Main Street $first $second", post = expected, unbounded = false))
            add(Case("123 ${first.first()} ${second.first()} Main Street", pre = expected))
            add(Case("123 $first $second Street", name = "$first $second".uppercase()))
        }
        for ((first, second) in listOf("North" to "South", "South" to "North", "East" to "West", "West" to "East")) {
            add(Case("123 $first $second Main Street", pre = first.first().toString(), name = "${second.uppercase()} MAIN"))
            add(Case("123 ${first.first()} ${second.first()} Main Street", pre = first.first().toString(), name = "${second.uppercase()} MAIN"))
            add(Case("123 Main Street $first $second", name = "MAIN ${second.uppercase()}", post = first.first().toString(), unbounded = false))
            add(Case("123 Main Street ${first.first()} ${second.first()}", name = "MAIN ${second.uppercase()}", post = first.first().toString(), unbounded = false))
        }
        for (letter in listOf("A", "B", "C", "E", "N", "S", "W")) {
            // Alphabet indicators cannot safely be expanded into direction words without AIS data.
            add(Case("123 Bay $letter Drive", name = "BAY $letter", suffix = "DR"))
            add(Case("123 Avenue $letter", name = "AVENUE $letter", suffix = "", unbounded = false))
            add(Case("123 Ave $letter", name = "AVENUE $letter", suffix = "", unbounded = false))
        }

        // https://pe.usps.com/text/pub28/28c2_015.htm (§234.2)
        for ((first, second, abbreviated) in listOf(
            Triple("Avenue", "Drive", "DR"), Triple("Street", "Circle", "CIR"),
            Triple("Park", "Road", "RD"), Triple("Lake", "Court", "CT"),
            Triple("Trail", "Lane", "LN"), Triple("Valley", "Way", "WAY"),
        )) {
            add(Case("123 Main $first $second", name = "MAIN ${first.uppercase()}", suffix = abbreviated))
            add(Case("123 Main $first $second West", name = "MAIN ${first.uppercase()}", suffix = abbreviated, post = "W", unbounded = false))
        }
        add(Case("789 Main Ave Drive", number = "789", name = "MAIN AVENUE", suffix = "DR"))
        add(Case("123 St Drive", name = "STREET", suffix = "DR"))
        add(Case("4513 3rd St Circle West", number = "4513", name = "3RD STREET", suffix = "CIR", post = "W", unbounded = false))
        add(Case("123 Main Parks Drive", name = "MAIN PARKS", suffix = "DR"))

        // https://pe.usps.com/text/pub28/28c2_016.htm (§235): preserve numeric names as supplied.
        for (name in listOf("1st", "2nd", "3rd", "7th", "11th", "21st", "101st", "Seventh", "Twenty First", "007")) {
            add(Case("123 $name Street", name = name.uppercase()))
            add(Case("123 North $name Avenue", pre = "N", name = name.uppercase(), suffix = "AVE"))
        }

        // https://pe.usps.com/text/pub28/28apd_002.htm through 28apd_005.htm (Appendix D)
        for (number in listOf("112-10", "289-01", "001-02", "12-A", "12-34-A", "123A", "A123", "12AB", "N6W23001", "S12W23456")) {
            add(Case("$number Bluemound Road", number = number, name = "BLUEMOUND", suffix = "RD"))
        }
        for (number in listOf("123 1/2", "123 3/4", "12 10/16", "1/2", "001 1/2", "123 1 / 2", "1 / 2")) {
            add(Case("$number Main Street", number = number.replace(" / ", "/")))
        }
        add(Case("842 E 1700 S", number = "842", pre = "E", name = "1700", suffix = "", post = "S", unbounded = false))
        add(Case("842 East 1700 South", number = "842", pre = "E", name = "1700", suffix = "", post = "S", unbounded = false))
        for (name in listOf("39.2", "39.4", "0.5", "001.25")) {
            add(Case("123 $name Road", name = name, suffix = "RD"))
        }

        // https://pe.usps.com/text/pub28/28c2_018.htm (§237)
        for ((alias, canonical) in listOf(
            "County Rd" to "COUNTY ROAD", "Cnty Hwy" to "COUNTY HIGHWAY", "Hwy" to "HIGHWAY",
            "I" to "INTERSTATE", "Rd" to "ROAD", "State Highway" to "STATE HIGHWAY",
            "State Rte" to "STATE ROUTE", "US Hwy" to "US HIGHWAY", "Township Rd" to "TOWNSHIP ROAD",
        )) {
            for (route in listOf("20", "007", "12A", "12-A")) {
                add(Case("123 $alias $route", name = "$canonical $route", suffix = ""))
                add(Case("123 N $alias $route W Apt 4", pre = "N", name = "$canonical $route", suffix = "", post = "W", unit = "APT", range = "4"))
            }
        }
        add(Case("123 County Road N East", name = "COUNTY ROAD N", suffix = "", post = "E", unbounded = false))
        add(Case("123 County Road N E", name = "COUNTY ROAD N", suffix = "", post = "E"))
        add(Case("123 County Highway AB", name = "COUNTY HIGHWAY AB", suffix = ""))
        add(Case("123 US 12AB", name = "US HIGHWAY 12AB", suffix = ""))
        add(Case("123 SR A", name = "STATE ROUTE A", suffix = ""))
        add(Case("123 SR-A", name = "STATE ROUTE A", suffix = ""))
        for ((input, name, suffix) in listOf(
            Triple("I Street", "I", "ST"), Triple("I Drive", "I", "DR"),
            Triple("I N Street", "I N", "ST"), Triple("US Road", "US", "RD"), Triple("Highway Road", "HIGHWAY", "RD"),
        )) add(Case("123 $input", name = name, suffix = suffix))
        add(Case("123 Highway 1 Key", name = "HIGHWAY 1", suffix = "KY", unbounded = false))
        add(Case("123 Highway 1 Key 4", name = "HIGHWAY 1", suffix = "", unit = "KEY", range = "4"))

        // https://pe.usps.com/text/pub28/28c2_003.htm and 28apc_003.htm (§213/C2)
        for ((full, abbreviated) in listOf(
            "Apartment" to "APT", "Building" to "BLDG", "Department" to "DEPT", "Floor" to "FL",
            "Hangar" to "HNGR", "Key" to "KEY", "Lot" to "LOT", "Pier" to "PIER", "Room" to "RM",
            "Slip" to "SLIP", "Space" to "SPC", "Stop" to "STOP", "Suite" to "STE", "Trailer" to "TRLR", "Unit" to "UNIT",
        )) {
            for (range in listOf("7", "A", "PH12", "A-12")) {
                add(Case("123 Main Street $full $range", unit = abbreviated, range = range))
                add(Case("123 Main Street $abbreviated #$range", unit = abbreviated, range = range))
            }
        }
        for ((full, abbreviated) in listOf(
            "Basement" to "BSMT", "Front" to "FRNT", "Lobby" to "LBBY", "Lower" to "LOWR", "Office" to "OFC",
            "Penthouse" to "PH", "Rear" to "REAR", "Side" to "SIDE", "Upper" to "UPPR",
        )) {
            add(Case("123 Main Street $full", unit = abbreviated))
            add(Case("123 Main Street $abbreviated", unit = abbreviated))
            add(Case("123 Main Street $full 7", unit = abbreviated, range = "7"))
        }
        for (range in listOf("7", "A", "12B", "A-12")) add(Case("123 Main Street #$range", unit = "#", range = range))
        for (name in listOf("Front", "Side", "Unit", "Key", "Rear", "Upper")) {
            add(Case("123 $name Street", name = name.uppercase()))
        }
    }

    @TestFactory
    fun `publication 28 street grammar preserves every component and delivery boundary`() = cases.flatMap { case ->
        val separators = if (case.unbounded) listOf(", ", "\n", " ") else listOf(", ", "\n")
        separators.map { separator ->
            val input = "${case.input}${separator}Austin TX 78701"
            dynamicTest(input.replace("\n", " / ")) {
                val parsed = parser.standardize(input.lowercase())
                assertEquals(case.expected, parsed)
                assertEquals(case.expected, parser.standardize(parsed.formatLines().joinToString("\n")))
            }
        }
    }

    @TestFactory
    fun `secondary lookahead cannot cross the delivery boundary into a single letter city`() =
        listOf("KEY" to "KY", "TRAILER" to "TRLR").map { (word, suffix) ->
            dynamicTest(word) {
                val expected = USStandardizedAddress("123", "", "HIGHWAY 1", suffix, "", "", "", "A", "TX", "78701")
                assertEquals(expected, parser.standardize("123 HIGHWAY 1 $word, A TX 78701"))
                assertEquals(expected, parser.standardize(expected.formatLines().joinToString("\n")))
                val puertoRico = USStandardizedAddress("123", "", "CALLE APT", "", "", "", "", "A", "PR", "00901")
                assertEquals(puertoRico, parser.standardize("123 CALLE APT, A PR 00901"))
            }
        }

    @TestFactory
    fun `Puerto Rico street type is followed by a name before a secondary unit`() =
        listOf("CALLE FRONT", "CALLE SIDE", "CALLE REAR", "AVENIDA FRONT").flatMap { name ->
            listOf("", " APT 4").map { unit ->
                dynamicTest("$name$unit") {
                    val expected = USStandardizedAddress("123", "", name, "", "", if (unit.isEmpty()) "" else "APT",
                        if (unit.isEmpty()) "" else "4", "SAN JUAN", "PR", "00901")
                    val address = "123 $name$unit\nSAN JUAN PR 00901"
                    assertEquals(expected, parser.standardize(address))
                    assertEquals(expected, parser.standardize(expected.formatLines().joinToString("\n")))
                }
            }
        }

    @TestFactory
    fun `Puerto Rico street types and unit markers cannot replace a missing street name`() =
        listOf("123 CALLE", "123 AVENIDA", "123 AVE", "123 CALLE APT 4", "123 AVENIDA UNIT 7").map { delivery ->
            dynamicTest(delivery) {
                assertFailsWith<InvalidAddressException> { parser.standardize("$delivery\nSAN JUAN PR 00901") }
            }
        }

    @TestFactory
    fun `unbounded highway localities cannot be consumed as street suffixes`() =
        listOf("LAKE CITY", "KEY WEST", "PARK CITY", "BAY CITY", "VALLEY CITY").map { city ->
            dynamicTest(city) {
                val expected = USStandardizedAddress("123", "", "HIGHWAY 70", "", "", "", "", city, "FL", "32024")
                assertEquals(expected, parser.standardize("123 HIGHWAY 70 $city FL 32024"))
                assertEquals(expected, parser.standardize(expected.formatLines().joinToString("\n")))
            }
        }

    @TestFactory
    fun `malformed nearby primary ranges and secondary units fail`() = buildList {
        for (number in listOf("-12", "12-", "12--34", "N6--W", "ABC", "1//2", "/2", "1/", "1/0", "1/00", "123 1/0", "123 1 / 00", "123.5")) {
            add("$number Main Street")
        }
        for (unit in listOf("Apartment", "Apartment #", "Apartment --12", "Apartment 12-", "Apartment 12--A", "##12", "#", "#-A")) {
            add("123 Main Street $unit")
        }
    }.map { delivery ->
        dynamicTest(delivery) {
            assertFailsWith<InvalidAddressException> { parser.standardize("$delivery, Austin TX 78701") }
        }
    }
}
