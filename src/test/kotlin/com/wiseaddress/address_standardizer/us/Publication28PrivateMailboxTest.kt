package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Publication 28 §285: https://pe.usps.com/text/pub28/28c2_040.htm */
class Publication28PrivateMailboxTest {
    private val parser = USStandardizer()
    private val locality = "HERNDON VA 22071-2716"

    @TestFactory
    fun `285 published CMRA examples retain physical and private mailbox identities`(): List<DynamicTest> = listOf(
        case("PMB 234\nRR 1 BOX 12\n$locality", route("1", "12", "234"), "RR 1 BOX 12 PMB 234"),
        case("#234\n10 MAIN ST STE 11\n$locality", street("10", "STE", "11", "234"), "10 MAIN ST STE 11 PMB 234"),
        case("123 MAIN ST #4545\n$locality", street("123", "#", "4545"), "123 MAIN ST # 4545"),
        case("PO BOX 159753 PMB 3571\n$locality", box("159753", "3571"), "PO BOX 159753 PMB 3571"),
        case("10 MAIN ST STE 11 PMB 234\n$locality", street("10", "STE", "11", "234"), "10 MAIN ST STE 11 PMB 234"),
        case("RR 1 BOX 12 PMB 596\n$locality", route("1", "12", "596"), "RR 1 BOX 12 PMB 596"),
    )

    @TestFactory
    fun `285 explicit private mailboxes may precede or follow the street`(): List<DynamicTest> = buildList {
        for (mailbox in listOf("234", "00234", "A", "4B", "10-2")) {
            val expected = street("123", mailbox = mailbox)
            for (separator in listOf("\n", ", ")) {
                add(case("PMB $mailbox${separator}123 Main Street$separator$locality", expected, "123 MAIN ST PMB $mailbox"))
                add(case("123 Main Street PMB $mailbox$separator$locality", expected, "123 MAIN ST PMB $mailbox"))
            }
            add(case("123 Main Street PMB $mailbox $locality", expected, "123 MAIN ST PMB $mailbox"))
        }
    }

    @TestFactory
    fun `285 a leading number sign stays unambiguous above physical secondary information`(): List<DynamicTest> = buildList {
        for ((identifier, range) in listOf("STE" to "11", "APT" to "4", "BLDG" to "2", "FL" to "3", "#" to "11")) {
            for (mailbox in listOf("234", "00234", "A", "4B", "10-2")) {
                val expected = street("10", identifier, range, mailbox)
                val delivery = "10 MAIN ST $identifier $range PMB $mailbox"
                add(case("10 MAIN ST $identifier $range PMB $mailbox, $locality", expected, delivery))
                add(case("PMB $mailbox\n10 MAIN ST $identifier $range\n$locality", expected, delivery))
                add(case("# $mailbox\n10 MAIN ST $identifier $range\n$locality", expected, delivery))
            }
        }
    }

    @TestFactory
    fun `285 private mailboxes coexist with assigned rural highway and postal boxes`(): List<DynamicTest> = buildList {
        for (mailbox in listOf("234", "00234", "4B")) {
            val rural = route("1", "0012A", mailbox)
            add(case("RR 01 BOX 0012A PMB $mailbox, $locality", rural, "RR 1 BOX 0012A PMB $mailbox"))
            add(case("PMB $mailbox\nRR 01 BOX 0012A\n$locality", rural, "RR 1 BOX 0012A PMB $mailbox"))
            add(case("# $mailbox\nRR 01 BOX 0012A\n$locality", rural, "RR 1 BOX 0012A PMB $mailbox"))
            val highway = route("68", "019-2B", mailbox, USAddressType.HIGHWAY_CONTRACT)
            add(case("HC 068 BOX 019-2B PMB $mailbox, $locality", highway, "HC 68 BOX 019-2B PMB $mailbox"))
            add(case("PMB $mailbox\nHC 068 BOX 019-2B\n$locality", highway, "HC 68 BOX 019-2B PMB $mailbox"))
            val postal = box("0017", mailbox)
            add(case("PO BOX 0017 PMB $mailbox, $locality", postal, "PO BOX 0017 PMB $mailbox"))
            add(case("PMB $mailbox\nPO BOX 0017\n$locality", postal, "PO BOX 0017 PMB $mailbox"))
        }
    }

    @TestFactory
    fun `285 same-line mailbox after physical secondary must use PMB instead of a second number sign`(): List<DynamicTest> = listOf(
        "10 MAIN ST STE 11 #234",
        "10 MAIN ST STE 11 # 234",
        "10 MAIN ST #11 #234",
        "10 MAIN ST APT 11 #234",
        "10 MAIN ST BLDG 11 #234",
        "RR 1 BOX 12 #596",
        "HC 68 BOX 12 #596",
        "PO BOX 159753 #3571",
    ).map { delivery -> invalidCase("$delivery\n$locality") }

    @TestFactory
    fun `incomplete duplicate and malformed private mailbox components fail visibly`(): List<DynamicTest> = listOf(
        "123 MAIN ST PMB",
        "123 MAIN ST PMB #",
        "123 MAIN ST PMB ##234",
        "123 MAIN ST PMB -234",
        "123 MAIN ST PMB 234-",
        "123 MAIN ST PMB 23--4",
        "123 MAIN ST PMB 234 PMB 235",
        "10 MAIN ST STE 11 PMB 234 PMB 235",
        "10 MAIN ST STE 11 APT 4 PMB 234",
        "RR 1 BOX 12 PMB",
        "RR 1 BOX 12 PMB 234 PMB 235",
        "PO BOX 159753 PMB",
        "PO BOX 159753 PMB 234 PMB 235",
        "PMB\n123 MAIN ST",
        "PMB 234\nPMB 235\n123 MAIN ST",
        "#234\nPMB 235\n123 MAIN ST STE 11",
        "PMB 234\n10 MAIN ST STE 11 PMB 235",
        "STE 11 PMB 234\n10 MAIN ST APT 4",
        "# 11 PMB 234\n10 MAIN ST STE 4",
    ).map { delivery -> invalidCase("$delivery\n$locality") }

    @TestFactory
    fun `mailbox support preserves ordinary number-sign units and physical hyphenated ranges`(): List<DynamicTest> = listOf(
        case("STE 11 PMB 234\n10 MAIN ST\n$locality", street("10", "STE", "11", "234"), "10 MAIN ST STE 11 PMB 234"),
        case("# 11 PMB 234\n10 MAIN ST\n$locality", street("10", "#", "11", "234"), "10 MAIN ST # 11 PMB 234"),
        case("PMB 234\n10 MAIN ST\nSTE 11\n$locality", street("10", "STE", "11", "234"), "10 MAIN ST STE 11 PMB 234"),
        case("# 234\n123 MAIN ST\n$locality", street("123", "#", "234"), "123 MAIN ST # 234"),
        case("10 MAIN ST STE 11-234, $locality", street("10", "STE", "11-234"), "10 MAIN ST STE 11-234"),
        case("10 MAIN ST STE 11-234 PMB 0017, $locality", street("10", "STE", "11-234", "0017"), "10 MAIN ST STE 11-234 PMB 0017"),
    )

    private fun street(number: String, unit: String = "", range: String = "", mailbox: String = "") =
        USStandardizedAddress(number, "", "MAIN", "ST", "", unit, range, "HERNDON", "VA", "22071-2716", privateMailbox = mailbox)

    private fun box(number: String, mailbox: String) =
        USStandardizedAddress("", "", "", "", "", "", "", "HERNDON", "VA", "22071-2716", USAddressType.PO_BOX, boxNumber = number, privateMailbox = mailbox)

    private fun route(number: String, box: String, mailbox: String, type: USAddressType = USAddressType.RURAL_ROUTE) =
        USStandardizedAddress("", "", "", "", "", "", "", "HERNDON", "VA", "22071-2716", type, routeNumber = number, boxNumber = box, privateMailbox = mailbox)

    private fun case(input: String, expected: USStandardizedAddress, delivery: String): DynamicTest =
        dynamicTest(input.replace('\n', '|')) {
            val actual = parser.standardize(input)
            assertEquals(expected, actual, input)
            assertEquals(listOf(delivery, locality), actual.formatLines(), input)
            assertEquals(expected, parser.standardize(actual.formatLines().joinToString("\n")), "Canonical round trip: $input")
        }

    private fun invalidCase(input: String): DynamicTest = dynamicTest(input.replace('\n', '|')) {
        assertFailsWith<InvalidAddressException>(input) { parser.standardize(input) }
    }
}
