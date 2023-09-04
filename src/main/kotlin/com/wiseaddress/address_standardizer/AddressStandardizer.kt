package com.wiseaddress.address_standardizer

/**
 * A generic address Standardizer. The implementations are model-specific, for which the
 * [US model][com.wiseaddress.address_standardizer.us.USStandardizer] serves as an example.
 */
interface AddressStandardizer {
    /**
     * Standardizes an address
     *
     * @param address the full address
     * @return a StandardizedAddress
     */
    fun standardize(address: String) : StandardizedAddress
}