package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.StandardizedAddress

/**
 * Standardized address format returned by a [USStandardizer]
 */
data class USStandardizedAddress(val primaryRange: String,
                                 val preDirection: String,
                                 val streetName: String,
                                 val suffix: String,
                                 val postDirection: String,
                                 val secondaryIdentifier: String,
                                 val secondaryRange: String,
                                 val city: String,
                                 val state: String,
                                 val postalCode: String):
    StandardizedAddress {

    override fun toString(): String {
//        return "$primaryRange $preDirection $streetName $suffix $postDirection $secondaryIdentifier $secondaryRange $city $state $postalCode".replace("\\s+".toRegex(), " ")
        // for testing purposes only:
        return "USStandardizedAddress(primaryRange='$primaryRange', preDirection='$preDirection', streetName='$streetName', suffix='$suffix', postDirection='$postDirection', secondaryIdentifier='$secondaryIdentifier', secondaryRange='$secondaryRange', city='$city', state='$state', postalCode='$postalCode')"
    }
}