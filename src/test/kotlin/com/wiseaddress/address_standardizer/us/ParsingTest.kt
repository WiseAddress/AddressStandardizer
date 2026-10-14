package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ParsingTest {
    private val parser = USStandardizer()

    private val examples = listOf(
        "  123\tmain street,\n Springfield, Illinois 62704  " to "123 MAIN ST SPRINGFIELD IL 62704",
        "123 N. Main St. S., Springfield IL 62704" to "123 N MAIN ST S SPRINGFIELD IL 62704",
        "123 North East Main Avenue South West, Austin TX 78701" to "123 NE MAIN AVE SW AUSTIN TX 78701",
        "123 North Street, Austin TX 78701" to "123 NORTH ST AUSTIN TX 78701",
        "123 North West Street, Austin TX 78701" to "123 NORTH WEST ST AUSTIN TX 78701",
        "123 North South Main Street Austin TX 78701" to "123 N SOUTH MAIN ST AUSTIN TX 78701",
        "123 Main St West Springfield MA 01001" to "123 MAIN ST WEST SPRINGFIELD MA 01001",
        "123 Main St W Springfield MA 01001" to "123 MAIN ST W SPRINGFIELD MA 01001",
        "123 Old Mill Farm Road, Cedar Rapids IA 52401" to "123 OLD MILL FARM RD CEDAR RAPIDS IA 52401",
        "123 Main Street, Lake City FL 32024" to "123 MAIN ST LAKE CITY FL 32024",
        "123 Main St, Key West FL 33040" to "123 MAIN ST KEY WEST FL 33040",
        "123 St Charles Ave, St Louis MO 63101" to "123 ST CHARLES AVE ST LOUIS MO 63101",
        "123 Oak Street Drive Springfield IL 62704" to "123 OAK STREET DR SPRINGFIELD IL 62704",
        "123 Broadway, New York NY 10001" to "123 BROADWAY NEW YORK NY 10001",
        "123 Old Broadway, New York NY 10001" to "123 OLD BROADWAY NEW YORK NY 10001",
        "123 Old Broadway, NY 10001" to "123 OLD BROADWAY NY 10001",
        "123 West Side Road, Austin TX 78701" to "123 W SIDE RD AUSTIN TX 78701",
        "123 North Front Street, Austin TX 78701" to "123 N FRONT ST AUSTIN TX 78701",
        "123 Long Key, Austin TX 78701" to "123 LONG KY AUSTIN TX 78701",
        "123 Broadway Apt 2 New York NY 10001" to "123 BROADWAY APT 2 NEW YORK NY 10001",
        "123 Broadway Apt 2 Lake City FL 32024" to "123 BROADWAY APT 2 LAKE CITY FL 32024",
        "123 Main St, Apt 2, Springfield IL 62704" to "123 MAIN ST APT 2 SPRINGFIELD IL 62704",
        "123 Main St, Apt 2 Springfield IL 62704" to "123 MAIN ST APT 2 SPRINGFIELD IL 62704",
        "123 Main St #12A Springfield IL 62704" to "123 MAIN ST # 12A SPRINGFIELD IL 62704",
        "123 Main St Apartment #A-12 Springfield IL 62704" to "123 MAIN ST APT A-12 SPRINGFIELD IL 62704",
        "123 Main St Suite PH12 Springfield IL 62704" to "123 MAIN ST STE PH12 SPRINGFIELD IL 62704",
        "123 Main St Rear Springfield IL 62704" to "123 MAIN ST REAR SPRINGFIELD IL 62704",
        "123A Main St Boston MA 02108" to "123A MAIN ST BOSTON MA 02108",
        "12-34 Main St Queens NY 11354" to "12-34 MAIN ST QUEENS NY 11354",
        "12-A Main St Boston MA 02108" to "12-A MAIN ST BOSTON MA 02108",
        "12 1/2 Main St Boston MA 02108" to "12 1/2 MAIN ST BOSTON MA 02108",
        "12 1 / 2 Main St Boston MA 02108" to "12 1/2 MAIN ST BOSTON MA 02108",
        "1 / 2 Main St Boston MA 02108" to "1/2 MAIN ST BOSTON MA 02108",
        "1/2 Main St Boston MA 02108" to "1/2 MAIN ST BOSTON MA 02108",
        "12 7th Avenue New York NY 10001" to "12 7TH AVE NEW YORK NY 10001",
        "123 Main St Washington District of Columbia 20001" to "123 MAIN ST WASHINGTON DC 20001",
        "123 Main St Charleston West Virginia 25301" to "123 MAIN ST CHARLESTON WV 25301",
        "123 Main St San Juan Puerto Rico 00901" to "123 MAIN ST SAN JUAN PR 00901",
        "123 Main St Springfield IL" to "123 MAIN ST SPRINGFIELD IL",
        "123 Main St Springfield 62704" to "123 MAIN ST SPRINGFIELD 62704",
        "123 Main St IL 62704" to "123 MAIN ST IL 62704",
        "123 Main St Boston MA 021081234" to "123 MAIN ST BOSTON MA 02108-1234",
        "123 Main St Boston MA 02108 1234" to "123 MAIN ST BOSTON MA 02108-1234",
        "123 Main St Boston MA 02108-1234" to "123 MAIN ST BOSTON MA 02108-1234",
        "101 County Rd 20 Austin TX 78701" to "101 COUNTY ROAD 20 AUSTIN TX 78701",
        "11216 Cnty Hwy 140 Austin TX 78701" to "11216 COUNTY HIGHWAY 140 AUSTIN TX 78701",
        "1501 Hwy 50 Austin TX 78701" to "1501 HIGHWAY 50 AUSTIN TX 78701",
        "220 I-680 Austin TX 78701" to "220 INTERSTATE 680 AUSTIN TX 78701",
        "22604 Rd 123 Austin TX 78701" to "22604 ROAD 123 AUSTIN TX 78701",
        "1650 State Highway 335 Austin TX 78701" to "1650 STATE HIGHWAY 335 AUSTIN TX 78701",
        "7777 State Rte 39 Austin TX 78701" to "7777 STATE ROUTE 39 AUSTIN TX 78701",
        "1155 US-70 W Apt 3 Austin TX 78701" to "1155 US HIGHWAY 70 W APT 3 AUSTIN TX 78701",
        "3000 Township Rd 20 Austin TX 78701" to "3000 TOWNSHIP ROAD 20 AUSTIN TX 78701",
        "123 N Farm To Market 12A, Austin TX 78701" to "123 N FM 12A AUSTIN TX 78701",
        "P.O. Box 00123 Boston MA 02108" to "PO BOX 00123 BOSTON MA 02108",
        "PO BOX 123 Boston, MA 02108" to "PO BOX 123 BOSTON MA 02108",
        "RR 2 BOX 12 Springfield, IL 62704" to "RR 2 BOX 12 SPRINGFIELD IL 62704",
        "Post Office Box L Boston MA 02108" to "PO BOX L BOSTON MA 02108",
        "Rural Route 03 Box 012A Springfield IL 62704" to "RR 3 BOX 012A SPRINGFIELD IL 62704",
        "Highway Contract Route 068 Box 45-1 Springfield IL 62704" to "HC 68 BOX 45-1 SPRINGFIELD IL 62704",
        "PSC 123 Box 456 APO AE 09012" to "PSC 123 BOX 456 APO AE 09012",
        "CMR 123 Box 456 FPO AP 96201" to "CPR 123 BOX 456 FPO AP 96201",
        "Unit 8400 Box 12 DPO AE 09498" to "UNIT 8400 BOX 12 DPO AE 09498",
    )

    @TestFactory
    fun `supported addresses format and round trip with delivery boundaries`() = examples.map { (input, expected) ->
        dynamicTest(input) {
            val parsed = parser.standardize(input)
            assertEquals(expected, parsed.format())
            assertEquals(parsed, parser.standardize(parsed.formatLines().joinToString("\n")))
        }
    }

    @TestFactory
    fun `invalid and unresolved addresses fail explicitly`() = listOf(
        "", " ", "123", "Main St Springfield IL 62704", "ABC Main St Springfield IL 62704",
        "123 Main St Springfield", "123 Main St Springfield IL ABCDE", "123 Main St Springfield IL 1234",
        "123 Main St Springfield IL 12345-ABCD", "123 Main St Springfield IL 123456",
        "123 Main St Springfield IL 1234-56789", "123 Main St Apt Springfield IL 62704",
        "123 Main St # Springfield IL 62704", "123 Main St Apt # Springfield IL 62704",
        "123 Main St #--1 Springfield IL 62704", "123 Main St ##12 Springfield IL 62704",
        "123 Main St Apt -TWO, Springfield IL 62704", "123 Old Broadway New York NY 10001",
        "123 Broadway Apt -TWO, Springfield IL 62704",
        "123 1/0 Main St Boston MA 02108", "123 1 / 0 Main St Boston MA 02108",
        "123.5 Main St Boston MA 02108", "123 Main St @ Springfield IL 62704",
        "123 Main.St, Springfield IL 62704",
        "PO Box Boston MA 02108", "RR 2 Springfield IL 62704", "HC 2 Box Springfield IL 62704",
        "PSC 123 Box 456 Boston MA 02108", "PSC 123 Box 456 APO NY 09012",
        "123 Main St Boston AE 09012", "123 Main St APO NY 09012", "x".repeat(4097),
    ).map { input -> dynamicTest(input.take(100).ifBlank { "blank input (${input.length} characters)" }) { assertFailsWith<InvalidAddressException> { parser.standardize(input) } } }

    @Test
    fun `fields represent street and special delivery structure`() {
        val street = parser.standardize("123 N Main Street S Apt A2, Springfield IL 62704")
        assertEquals("N", street.preDirection)
        assertEquals("MAIN", street.streetName)
        assertEquals("ST", street.suffix)
        assertEquals("S", street.postDirection)
        assertEquals("A2", street.secondaryRange)
        assertEquals("SPRINGFIELD", street.city)
        val keyWest = parser.standardize("123 Main St, Key West FL 33040")
        assertEquals("MAIN", keyWest.streetName)
        assertEquals("ST", keyWest.suffix)
        assertEquals("", keyWest.secondaryIdentifier)
        assertEquals("KEY WEST", keyWest.city)
        val unitLine = parser.standardize("123 Main St, Apt 2 Springfield IL 62704")
        assertEquals("APT", unitLine.secondaryIdentifier)
        assertEquals("2", unitLine.secondaryRange)
        assertEquals("SPRINGFIELD", unitLine.city)
        val suffixlessUnit = parser.standardize("123 Broadway Apt 2 Lake City FL 32024")
        assertEquals("BROADWAY", suffixlessUnit.streetName)
        assertEquals("APT", suffixlessUnit.secondaryIdentifier)
        assertEquals("2", suffixlessUnit.secondaryRange)
        assertEquals("LAKE CITY", suffixlessUnit.city)
        val box = parser.standardize("PO BOX 12 Boston MA 02108")
        assertEquals(USAddressType.PO_BOX, box.addressType)
        assertEquals("12", box.boxNumber)
        assertEquals("", box.primaryRange)
        val route = parser.standardize("RR 2 BOX 12 Boston MA 02108")
        assertEquals(USAddressType.RURAL_ROUTE, route.addressType)
        assertEquals("2", route.routeNumber)
    }

    @Test
    fun `normalization is independent of locale`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("123 MAIN ST SPRINGFIELD IL 62704", parser.standardize("123 main st springfield il 62704").format())
        } finally { Locale.setDefault(before) }
    }

    @Test
    fun `one instance is safe across concurrent successes and failures`() {
        Executors.newFixedThreadPool(4).use { executor ->
            val futures = executor.invokeAll((0 until 400).map { index -> Callable {
                if (index % 3 == 0) assertFailsWith<InvalidAddressException> { parser.standardize("123 Main St Apt Springfield IL") }
                val (input, expected) = examples[index % examples.size]
                assertEquals(expected, parser.standardize(input).format())
            } })
            futures.forEach { it.get() }
        }
    }

    @Test
    fun `truncated and noisy inputs never leak collection or indexing failures`() {
        val random = Random(42)
        val words = listOf("123", "N", "MAIN", "ST", "APT", "#", "/", "IL", "62704", "PO", "BOX", "RR", "-", "X")
        repeat(2000) {
            val input = List(random.nextInt(0, 10)) { words.random(random) }.joinToString(" ")
            try {
                val result = parser.standardize(input)
                assertTrue(result.format().isNotBlank())
            } catch (_: InvalidAddressException) { }
        }
    }
}
