package com.wiseaddress.address_standardizer.us

import kotlin.test.Test
import kotlin.test.assertEquals

class USStandardizerTest {
    @Test
    fun `standardizes a street address using the bundled model`() {
        val address = USStandardizer().standardize("123 Main Street Springfield IL 62704")

        assertEquals(
            USStandardizedAddress("123", "", "MAIN", "ST", "", "", "", "SPRINGFIELD", "IL", "62704"),
            address,
        )
    }

    @Test
    fun `standardizes the original example with directions unit and ZIP plus four`() {
        val address = USStandardizer().standardize(
            "789 North South Oak Street Drive # 123-A West Springfield 12345 6789",
        )

        assertEquals(
            USStandardizedAddress(
                "789", "N", "SOUTH OAK STREET", "DR", "", "#", "123-A", "WEST SPRINGFIELD", "", "12345-6789",
            ),
            address,
        )
    }
}
