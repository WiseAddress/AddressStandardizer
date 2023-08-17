package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.AddressStandardizer
import com.wiseaddress.address_standardizer.model.Model

class USStandardizer : AddressStandardizer {

    private fun getPrimaryRange(splitAddress: MutableList<String>) : String {
        splitAddress[0]
    }

    override fun standardize(adr : String): USStandardizedAddress {
        var primaryRange = ""
        var preDirection = ""
        var streetName = ""
        var suffix = ""
        var postDirection = ""
        var secondaryIdentifier = ""
        var secondaryRange = ""
        var city = ""
        var state = ""
        var postalCode = ""
        // Do stuff here

        var newAddress = adr.uppercase()
        var splitAddress = newAddress.split(' ').toMutableList()

        return USStandardizedAddress(primaryRange, preDirection, streetName, suffix, postDirection, secondaryIdentifier, secondaryRange, city, state, postalCode)
    }
}