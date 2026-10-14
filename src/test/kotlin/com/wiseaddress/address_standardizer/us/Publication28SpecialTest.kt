package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * USPS Publication 28 §§225, 238–239, 24–26, 28, and 295.6–295.7.
 * https://pe.usps.com/text/pub28/welcome.htm
 */
class Publication28SpecialTest {
    private val standardizer = USStandardizer()

    @TestFactory
    fun `published delivery examples retain their specified normalization`(): List<DynamicTest> = listOf(
        specialCase("RFD ROUTE 4 #87A, Boston MA 02108", "RR 4 BOX 87A", USAddressType.RURAL_ROUTE, "4", "87A"),
        specialCase("HC068 BOX 98D, Boston MA 02108", "HC 68 BOX 98D", USAddressType.HIGHWAY_CONTRACT, "68", "98D"),
        specialCase("HIGHWAY CONTRACT ROUTE 68 BOX 23A, Boston MA 02108", "HC 68 BOX 23A", USAddressType.HIGHWAY_CONTRACT, "68", "23A"),
        specialCase("STAR ROUTE 68 BOX # 45, Boston MA 02108", "HC 68 BOX 45", USAddressType.HIGHWAY_CONTRACT, "68", "45"),
        specialCase("DRAWER L, Boston MA 02108", "PO BOX L", USAddressType.PO_BOX, box = "L"),
        specialCase("PO BOX 00145, Boston MA 02108", "PO BOX 00145", USAddressType.PO_BOX, box = "00145"),
        specialCase("GENERAL DELIVERY, TAMPA FL 33602-9999", "GENERAL DELIVERY", USAddressType.GENERAL_DELIVERY, city = "TAMPA", state = "FL", zip = "33602-9999"),
        specialCase("RFD ROUTE 4 BZN 1725, San Juan PR 00901", "RR 4 BOX 1725", USAddressType.RURAL_ROUTE, "4", "1725", city = "SAN JUAN", state = "PR", zip = "00901"),
    )

    @TestFactory
    fun `281 and 283 postal box designations retain the assigned identifier`(): List<DynamicTest> = buildList {
        for (prefix in listOf("PO BOX", "P.O. BOX", "P O BOX", "POST OFFICE BOX", "CALLER", "FIRM CALLER", "BIN", "LOCKBOX", "DRAWER")) {
            for (box in listOf("0017", "831", "L")) {
                for (separator in listOf(" ", ", ", "\n")) {
                    val input = "$prefix $box${separator}Boston MA 02108"
                    add(specialCase(input, "PO BOX $box", USAddressType.PO_BOX, box = box))
                }
            }
        }
    }

    @TestFactory
    fun `281 letter boxes and 282 significant zeros are not coerced to numbers`(): List<DynamicTest> =
        (('A'..'Z').map(Char::toString) + listOf("0", "0000", "000123", "999999999999999999999999999999"))
            .map { box -> specialCase("PO BOX $box, Boston MA 02108", "PO BOX $box", USAddressType.PO_BOX, box = box) }

    @TestFactory
    fun `241 to 244 rural aliases and box hyphens normalize without losing identifiers`(): List<DynamicTest> = buildList {
        for (prefix in listOf("RR", "RURAL ROUTE", "RFD", "RFD ROUTE", "RD", "RD ROUTE")) {
            for (box in listOf("0007", "87A", "19-2", "003A-04B")) {
                add(specialCase("$prefix 003 BOX $box, Boston MA 02108", "RR 3 BOX $box", USAddressType.RURAL_ROUTE, "3", box))
            }
        }
        for (prefix in listOf("RR003", "RFD003", "RD003")) {
            add(specialCase("$prefix BOX 007, Boston MA 02108", "RR 3 BOX 007", USAddressType.RURAL_ROUTE, "3", "007"))
        }
        for (routeMarker in listOf("NUMBER ", "NO. ", "#", "# ")) {
            for (boxMarker in listOf("NUMBER ", "NO. ", "#", "# ")) {
                add(specialCase("RR ${routeMarker}003 BOX ${boxMarker}007, Boston MA 02108", "RR 3 BOX 007", USAddressType.RURAL_ROUTE, "3", "007"))
            }
        }
    }

    @TestFactory
    fun `251 to 253 highway contract aliases preserve letters and box hyphens`(): List<DynamicTest> = buildList {
        for (prefix in listOf("HC", "HC ROUTE", "HIGHWAY CONTRACT", "HIGHWAY CONTRACT ROUTE", "STAR ROUTE")) {
            for ((suppliedRoute, route) in listOf("068" to "68", "068A" to "68A", "000" to "0")) {
                for (box in listOf("0007", "87A", "19-2", "003A-04B")) {
                    add(specialCase("$prefix $suppliedRoute BOX $box, Boston MA 02108", "HC $route BOX $box", USAddressType.HIGHWAY_CONTRACT, route, box))
                }
            }
        }
        for (marker in listOf("NUMBER ", "NO. ", "#", "# ")) {
            add(specialCase("HC ${marker}0068A BOX ${marker}0007, Boston MA 02108", "HC 68A BOX 0007", USAddressType.HIGHWAY_CONTRACT, "68A", "0007"))
        }
    }

    @TestFactory
    fun `225 and 238 to 239 military localities preserve unit and box zeros`(): List<DynamicTest> = buildList {
        for (unit in listOf("CPR", "OPC", "PSC", "UPR", "UNIT", "CMR")) {
            for ((city, state, zip) in listOf(Triple("APO", "AE", "09012"), Triple("FPO", "AP", "96201"), Triple("DPO", "AA", "34004"))) {
                for (separator in listOf(" ", "\n")) {
                    val normalizedUnit = if (unit == "CMR") "CPR" else unit
                    add(specialCase("$unit 0802 BOX 0074$separator$city $state $zip", "$normalizedUnit 0802 BOX 0074", USAddressType.MILITARY, "0802", "0074", normalizedUnit, city, state, zip))
                }
            }
        }
    }

    @TestFactory
    fun `261 general delivery remains fully spelled out and does not fabricate ZIP data`(): List<DynamicTest> = buildList {
        for (input in listOf("GENERAL DELIVERY, Boston MA 02108", "general delivery\nboston ma 02108", "General Delivery Boston Massachusetts 02108")) {
            add(specialCase(input, "GENERAL DELIVERY", USAddressType.GENERAL_DELIVERY))
        }
        for (zip in listOf("02108-9999", "021089999", "02108 9999")) {
            add(specialCase("GENERAL DELIVERY, Boston MA $zip", "GENERAL DELIVERY", USAddressType.GENERAL_DELIVERY, zip = "02108-9999"))
        }
        add(specialCase("GENERAL DELIVERY, Boston MA", "GENERAL DELIVERY", USAddressType.GENERAL_DELIVERY, zip = ""))
    }

    @TestFactory
    fun `295 point 6 Puerto Rico box aliases normalize in Puerto Rico`(): List<DynamicTest> = buildList {
        for (prefix in listOf("APARTADO", "APTDO", "BUZON", "BOX", "CALL BOX", "GPO BOX")) {
            for (box in listOf("0017", "831", "L")) {
                add(specialCase("$prefix $box, San Juan PR 00901", "PO BOX $box", USAddressType.PO_BOX, box = box, city = "SAN JUAN", state = "PR", zip = "00901"))
            }
        }
    }

    @TestFactory
    fun `293 and 295 point 7 Puerto Rico rural names and box markers normalize`(): List<DynamicTest> = buildList {
        for (prefix in listOf("RUTA RURAL", "RURAL", "RT", "RR", "RD", "RFD")) {
            for (marker in listOf("BOX", "BUZON", "BZN")) {
                add(specialCase("$prefix 003 $marker 007A, San Juan PR 00901", "RR 3 BOX 007A", USAddressType.RURAL_ROUTE, "3", "007A", city = "SAN JUAN", state = "PR", zip = "00901"))
            }
        }
        add(specialCase("Ruta Estrella 0068A Buzon 001-2, San Juan PR 00901", "HC 68A BOX 001-2", USAddressType.HIGHWAY_CONTRACT, "68A", "001-2", city = "SAN JUAN", state = "PR", zip = "00901"))
        add(specialCase("Entrega General, San Juan PR 00901-9999", "GENERAL DELIVERY", USAddressType.GENERAL_DELIVERY, city = "SAN JUAN", state = "PR", zip = "00901-9999"))
    }

    // E11 applies E12 range forms to rural and highway-contract receptacles.
    @TestFactory
    fun `E12 alphabetic and mixed route box identifiers preserve assigned spelling`(): List<DynamicTest> = buildList {
        for ((prefix, type) in listOf("RR" to USAddressType.RURAL_ROUTE, "HC" to USAddressType.HIGHWAY_CONTRACT)) {
            for (box in listOf("A", "L", "AAA", "AAD", "AB90", "15AB", "101AAA", "A1A", "001AA", "1A-2B-3C", "1-2-3", "23AA-04BB")) {
                add(specialCase("$prefix 001 BOX $box, Boston MA 02108", "$prefix 1 BOX $box", type, "1", box))
            }
            add(invalidCase("$prefix 1 BOX AAA BOSTON MA 02108"))
        }
    }

    @TestFactory
    fun `malformed special delivery identifiers and extra delivery words fail visibly`(): List<DynamicTest> =
        listOf(
            "PO BOX", "PO BOX AB", "PO BOX 1A", "PO BOX 1-2", "PO BOX -17", "PO BOX 12/3", "PO BOX ##17",
            "PO BOX 17 APT 4", "PO BOX 17 MAIN ST", "RR 1", "RR 1 BOX", "RR A BOX 1", "RR 1A BOX 1",
            "RR 1 BOX -1", "RR 1 BOX 1-", "RR 1 BOX 1--2",
            "RR 1 BOX 1 MAIN ST", "HC A BOX 1", "HC 1 BOX 1 MAIN ST",
            "HC 1 BOX 1--2", "GENERAL", "GENERAL DELIVERY BOX 1", "GENERAL DELIVERY APT 1",
            "GEN DELIVERY", "GEN DEL", "GENERAL DEL", "GENERAL DELIVERY EXTRA", "GENERAL DELIVERY GENERAL DELIVERY",
        ).map { delivery -> invalidCase("$delivery, Boston MA 02108") }

    @TestFactory
    fun `225 military addresses require a ZIP and a paired military locality`(): List<DynamicTest> = buildList {
        for (unit in listOf("CPR", "OPC", "PSC", "UPR", "UNIT")) {
            for (locality in listOf("APO AE", "APO NY 09012", "BOSTON AE 09012", "AE 09012", "APO 09012")) {
                add(invalidCase("$unit 123 BOX 456, $locality"))
            }
            for (delivery in listOf("$unit 123", "$unit 123 BOX", "$unit 123A BOX 456", "$unit 123 BOX 456A", "$unit 123 #456")) {
                add(invalidCase("$delivery, APO AE 09012"))
            }
        }
        add(invalidCase("GENERAL DELIVERY, Boston MA 02108-1234"))
    }

    @TestFactory
    fun `Puerto Rico aliases are not guessed for mainland addresses`(): List<DynamicTest> =
        listOf("APARTADO 1", "APTDO 1", "BUZON 1", "BOX 1", "GPO BOX 1", "CALL BOX 1", "RT 1 BOX 2", "RUTA RURAL 1 BOX 2", "ENTREGA GENERAL")
            .map { invalidCase("$it, Boston MA 02108") }

    @Test
    fun `284 PO box street addressing retains its distinct street syntax`() {
        for (marker in listOf("#", "UNIT")) {
            val result = standardizer.standardize("123 Main Street $marker 0042, Boston MA 02108")
            assertEquals(USAddressType.STREET, result.addressType)
            assertEquals("123", result.primaryRange)
            assertEquals("MAIN", result.streetName)
            assertEquals("ST", result.suffix)
            assertEquals(marker, result.secondaryIdentifier)
            assertEquals("0042", result.secondaryRange)
            assertEquals("", result.boxNumber)
            assertEquals("123 MAIN ST $marker 0042", result.formatLines()[0])
        }
    }

    @Test
    fun `general delivery value cannot carry hidden route or box fields`() {
        val general = standardizer.standardize("GENERAL DELIVERY, Boston MA 02108")
        assertFailsWith<IllegalArgumentException> { general.copy(boxNumber = "17") }
        assertFailsWith<IllegalArgumentException> { general.copy(routeNumber = "1") }
        assertFailsWith<IllegalArgumentException> { general.copy(militaryUnit = "PSC") }
        assertFailsWith<IllegalArgumentException> { general.copy(primaryRange = "123", streetName = "MAIN") }
        assertFailsWith<IllegalArgumentException> { general.copy(addressType = USAddressType.PO_BOX) }
    }

    private fun specialCase(
        input: String,
        delivery: String,
        type: USAddressType,
        route: String = "",
        box: String = "",
        unit: String = "",
        city: String = "BOSTON",
        state: String = "MA",
        zip: String = "02108",
    ): DynamicTest = DynamicTest.dynamicTest(input.replace('\n', '|')) {
        val expected = USStandardizedAddress("", "", "", "", "", "", "", city, state, zip, type, route, box, unit)
        val actual = standardizer.standardize(input)
        assertEquals(expected, actual, input)
        assertEquals(listOf(delivery, listOf(city, state, zip).filter(String::isNotEmpty).joinToString(" ")), actual.formatLines(), input)
        assertEquals(expected, standardizer.standardize(actual.formatLines().joinToString("\n")), "Canonical round trip: $input")
    }

    private fun invalidCase(input: String): DynamicTest = DynamicTest.dynamicTest(input) {
        assertFailsWith<InvalidAddressException>(input) { standardizer.standardize(input) }
    }
}
