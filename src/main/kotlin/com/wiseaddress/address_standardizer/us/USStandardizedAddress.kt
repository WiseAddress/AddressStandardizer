package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.StandardizedAddress

/**
 * Standardized Address Format returned by a US Address Standardizer
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
        return "$primaryRange $preDirection $streetName $suffix $postDirection $secondaryIdentifier $secondaryRange $city $state $postalCode".replace("\\s+", " ")
    }

    }