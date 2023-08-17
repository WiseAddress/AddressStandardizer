package com.wiseaddress.address_standardizer

import java.io.Serializable

/**
 * The most basic StandardizedAddress. Literally just an interface to be extended upon...
 * Derive only data classes from this interface for each model.
 * @see com.wiseaddress.address_standardizer.us.USStandardizedAddress
 */
interface StandardizedAddress: Serializable {
    override fun toString(): String
}