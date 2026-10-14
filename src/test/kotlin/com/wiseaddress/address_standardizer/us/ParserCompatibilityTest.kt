package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import com.wiseaddress.address_standardizer.model.Model
import com.wiseaddress.address_standardizer.model.ModelFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParserCompatibilityTest {
    private val parser = USStandardizer()

    @Test
    fun `uppercase conversion preserves expanding letters and accented names`() {
        val address = parser.standardize("123 Straße, München IL 62704")
        assertEquals("STRASSE", address.streetName)
        assertEquals("MÜNCHEN", address.city)
        assertEquals("FFI", parser.standardize("123 ﬃ Street, Boston MA 02108").streetName)
        assertEquals("CAFÉ", parser.standardize("123 Café Street, Boston MA 02108").streetName)
        assertFailsWith<InvalidAddressException> {
            parser.standardize("123 Cafe\u0301 Street, Boston MA 02108")
        }
    }

    @Test
    fun `Unicode spaces separate words but only explicit line breaks separate delivery and city`() {
        val input = "123 Main Street, Boston MA 02108"
        val expected = parser.standardize(input)
        for (space in listOf('\u00A0', '\u202F')) {
            assertEquals(expected, parser.standardize(input.replace(' ', space)))
        }
        val suffixless = parser.standardize("123 Old Broadway\r\nNew York NY 10001")
        assertEquals("OLD BROADWAY", suffixless.streetName)
        assertEquals("NEW YORK", suffixless.city)
        assertFailsWith<InvalidAddressException> {
            parser.standardize("123 Old Broadway\u2028New York NY 10001")
        }
        assertEquals(expected, parser.standardize(",,123 Main St,,,\r\n\r\nBoston,, MA 02108,,"))
    }

    @Test
    fun `initials and trailing periods normalize without accepting arbitrary embedded periods`() {
        assertEquals("123 MAIN ST BOSTON MA 02108", parser.standardize("123 Main... St..., Boston MA 02108").format())
        assertEquals("A", parser.standardize("123 A... St, Boston MA 02108").streetName)
        val box = parser.standardize("P.O Box 00123, Boston MA 02108")
        assertEquals(USAddressType.PO_BOX, box.addressType)
        assertEquals("00123", box.boxNumber)
        for (delivery in listOf("123 Main.St", "123. Main St", "123 Main . St")) {
            assertFailsWith<InvalidAddressException>(delivery) {
                parser.standardize("$delivery, Boston MA 02108")
            }
        }
    }

    @Test
    fun `Unicode digits are names but not house numbers or ZIP codes`() {
        assertEquals("٤TH", parser.standardize("123 ٤TH Street, Boston MA 02108").streetName)
        for (input in listOf("١٢٣ Main Street, Boston MA 02108", "123 Main Street, Boston MA ٠٢١٠٨")) {
            assertFailsWith<InvalidAddressException>(input) { parser.standardize(input) }
        }
        assertEquals("12-34-A", parser.standardize("12-34-A Main St, Boston MA 02108").primaryRange)
        assertEquals("0/2", parser.standardize("0/2 Main St, Boston MA 02108").primaryRange)
        assertFailsWith<InvalidAddressException> { parser.standardize("1/00 Main St, Boston MA 02108") }
    }

    @Test
    fun `unit ranges retain their distinct letter and hyphen rules`() {
        for (unit in listOf("A", "A-1-B")) {
            assertEquals(unit, parser.standardize("123 Main St Apt $unit, Boston MA 02108").secondaryRange)
        }
        assertEquals("AB", parser.standardize("123 Main St Apt AB, Boston MA 02108").secondaryRange)
        for (unit in listOf("A-B", "A-1-")) {
            assertFailsWith<InvalidAddressException>(unit) {
                parser.standardize("123 Main St Apt $unit, Boston MA 02108")
            }
        }
        assertEquals("US HIGHWAY 12AB", parser.standardize("123 US 12AB, Austin TX 78701").streetName)
    }

    @Test
    fun `custom aliases stay isolated and preserve longest matching after caller mutation`() {
        val source = ModelFactory.load()
        val streets = source.street_abbreviations.toMutableMap().apply { put("RUE", "ST") }
        val states = source.states.toMutableMap().apply {
            put("EAST VIRGINIA", "VA")
            put("COMMONWEALTH OF EAST VIRGINIA", "VA")
        }
        val directions = source.directions.toMutableMap().apply { put("UPTOWN", "N") }
        val highways = source.csl.toMutableMap().apply {
            put("SCENIC", "HIGHWAY")
            put("SCENIC ROAD", "COUNTY ROAD")
            put("SCENIC EAST ROAD", "US HIGHWAY")
        }
        val custom = USStandardizer(Model(
            streets, source.secondary_units, source.secondary_range_unneeded, states,
            directions, source.miscellaneous, highways,
        ))
        streets.clear()
        states.clear()
        directions.clear()
        highways.clear()

        val input = "123 Main Rue, Boston MA 02108"
        assertEquals("ST", custom.standardize(input).suffix)
        assertEquals("RUE", parser.standardize(input).suffix)
        val address = custom.standardize("123 Uptown Scenic East Road 07A, Richmond Commonwealth Of East Virginia 23220")
        assertEquals("N", address.preDirection)
        assertEquals("US HIGHWAY 07A", address.streetName)
        assertEquals("RICHMOND", address.city)
        assertEquals("VA", address.state)
        assertEquals("COUNTY ROAD 07A", custom.standardize("123 Scenic Road 07A, Austin TX 78701").streetName)
    }
}
