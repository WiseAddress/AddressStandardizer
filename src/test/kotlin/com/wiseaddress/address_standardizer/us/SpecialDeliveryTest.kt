package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SpecialDeliveryTest {
    @Test
    fun `PO boxes preserve significant zeros and accept letter identifiers`() {
        assertEquals(
            SpecialDelivery(USAddressType.PO_BOX, boxNumber = "00145", consumed = 3),
            parse("PO BOX 00145 CHICAGO"),
        )
        assertEquals(
            SpecialDelivery(USAddressType.PO_BOX, boxNumber = "L", consumed = 4),
            parse("POST OFFICE BOX L CHICAGO"),
        )
        assertEquals(4, parse("P O BOX 123 CHICAGO")?.consumed)
    }

    @Test
    fun `postal box aliases and number markers normalize consistently`() {
        for (prefix in listOf("CALLER", "FIRM CALLER", "BIN", "LOCKBOX", "DRAWER", "PO BOX")) {
            for (value in listOf("123", "#123", "# 123")) {
                val delivery = assertNotNull(parse("$prefix $value CHICAGO"))
                assertEquals(USAddressType.PO_BOX, delivery.type)
                assertEquals("123", delivery.boxNumber)
                assertEquals("CHICAGO", "$prefix $value CHICAGO".split(' ')[delivery.consumed])
            }
        }
    }

    @Test
    fun `rural aliases remove route zeros and preserve box suffixes and hyphens`() {
        for (prefix in listOf("RR", "RURAL ROUTE", "RFD", "RFD ROUTE", "RD")) {
            val delivery = assertNotNull(parse("$prefix 04 BOX 19-1A SPRINGFIELD"))
            assertEquals(USAddressType.RURAL_ROUTE, delivery.type)
            assertEquals("4", delivery.routeNumber)
            assertEquals("19-1A", delivery.boxNumber)
            assertEquals("", delivery.militaryUnit)
            assertEquals("SPRINGFIELD", "$prefix 04 BOX 19-1A SPRINGFIELD".split(' ')[delivery.consumed])
        }
    }

    @Test
    fun `compact rural routes and legacy number markers are supported`() {
        assertEquals(
            SpecialDelivery(USAddressType.RURAL_ROUTE, "3", "98D", consumed = 3),
            parse("RR03 BOX 98D SPRINGFIELD"),
        )
        assertEquals("87A", parse("RFD ROUTE 4 #87A SPRINGFIELD")?.boxNumber)
        assertEquals("87A", parse("RD NUMBER 4 BOX # 87A SPRINGFIELD")?.boxNumber)
        assertEquals("87A", parse("RR NO 4 BOX 87A SPRINGFIELD")?.boxNumber)
        assertEquals("87A", parse("RR #4 BOX 87A SPRINGFIELD")?.boxNumber)
    }

    @Test
    fun `highway contract aliases preserve assigned box and normalize route`() {
        for (prefix in listOf("HC", "HC ROUTE", "HIGHWAY CONTRACT", "HIGHWAY CONTRACT ROUTE", "STAR ROUTE")) {
            val delivery = assertNotNull(parse("$prefix 068 BOX 19-2B SPRINGFIELD"))
            assertEquals(USAddressType.HIGHWAY_CONTRACT, delivery.type)
            assertEquals("68", delivery.routeNumber)
            assertEquals("19-2B", delivery.boxNumber)
        }
        assertEquals("68A", parse("HC068A BOX 23A SPRINGFIELD")?.routeNumber)
        assertEquals("0", parse("HC000 BOX 001 SPRINGFIELD")?.routeNumber)
        assertEquals("001", parse("HC000 BOX 001 SPRINGFIELD")?.boxNumber)
    }

    @Test
    fun `military units preserve assigned numbers and stop before military city`() {
        for (unit in listOf("CPR", "OPC", "PSC", "UPR", "UNIT")) {
            assertEquals(
                SpecialDelivery(USAddressType.MILITARY, "0802", "0074", unit, 4),
                parse("$unit 0802 BOX 0074 APO"),
            )
        }
    }

    @Test
    fun `legacy military CMR is renamed to CPR`() {
        assertEquals(
            SpecialDelivery(USAddressType.MILITARY, "830", "51", "CPR", 4),
            parse("CMR 830 BOX 51 APO"),
        )
    }

    @Test
    fun `numeric identifiers are preserved without integer conversion`() {
        assertEquals("000", parse("PO BOX 000 CHICAGO")?.boxNumber)
        assertEquals(
            SpecialDelivery(USAddressType.RURAL_ROUTE, "0", "000", consumed = 4),
            parse("RR 000 BOX 000 CHICAGO"),
        )
        assertEquals(
            SpecialDelivery(USAddressType.MILITARY, "000", "000", "PSC", 4),
            parse("PSC 000 BOX 000 APO"),
        )
        val largeNumber = "999999999999999999999999999999"
        assertEquals(largeNumber, parse("PO BOX $largeNumber CHICAGO")?.boxNumber)
        assertEquals(largeNumber, parse("HC $largeNumber BOX 1 CHICAGO")?.routeNumber)
    }

    @Test
    fun `route letters and hyphens preserve numeric segments and significant box zeros`() {
        assertEquals(
            SpecialDelivery(USAddressType.HIGHWAY_CONTRACT, "0A", "023A-004B", consumed = 4),
            parse("HC 000A BOX 023A-004B CHICAGO"),
        )
        for (box in listOf("23AA-4B", "23A-4BB", "23A-4B-5", "\u0661")) {
            assertFailsWith<InvalidAddressException>(box) { parse("HC 1 BOX $box CHICAGO") }
        }
    }

    @Test
    fun `recognized incomplete forms fail with domain errors`() {
        val malformed = listOf(
            "PO", "PO BOX", "PO BOX CHICAGO", "PO BOX -0145", "PO BOX 12/3", "P O", "POST OFFICE",
            "RR", "RR BOX 3", "RR 1", "RR 1 BOX", "RR 1 BOX CHICAGO", "RR 1A BOX 2", "RR 1 BOX 2--A",
            "RURAL 1 BOX 2", "RR01", "HC", "HC X BOX 2", "HIGHWAY CONTRACT ROUTE", "STAR ROUTE 4",
            "RR L BOX 1", "RR 1 BOX L", "HC L BOX 1", "HC 1 BOX L", "PO BOX AB", "PO BOX ##123",
            "PSC", "PSC 3", "PSC 3 BOX", "PSC X BOX 3", "PSC 3 BOX X", "PSC 3A BOX 1", "PSC 3 BOX 1A",
            "UNIT 2 #3 APO", "CMR 2 APO",
        )
        for (input in malformed) {
            assertFailsWith<InvalidAddressException>(input) { parse(input) }
        }
    }

    @Test
    fun `ordinary numbered streets are left to the street parser`() {
        for (input in listOf("123 PO BOX RD", "10 RURAL ROUTE DR", "7 UNIT ST", "HIGHWAY 4", "STAR ST", "POST ST")) {
            assertNull(parse(input), input)
        }
        assertNull(parseSpecialDelivery(emptyList()))
    }

    private fun parse(input: String): SpecialDelivery? = parseSpecialDelivery(input.split(' '))
}
