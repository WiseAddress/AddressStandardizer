package com.wiseaddress.address_standardizer

interface AddressStandardizer {
    /**
     * Standardizes an address
     *
     * @param adr The full address
     * @return A StandardizedAddress
     */
    fun standardize(adr: String) : StandardizedAddress
}