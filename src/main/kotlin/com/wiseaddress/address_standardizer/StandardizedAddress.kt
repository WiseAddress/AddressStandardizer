package com.wiseaddress.address_standardizer

import java.io.Serializable

/**
 * A mostly empty interface for storing standardized address output from the corresponding Standardizer.
 * Only mandates that a proper toString method is defined, as the specific data held is dependent on the
 * types of addresses. Derive only data classes from this for each model.
 *
 * The [US model][com.wiseaddress.address_standardizer.us.USStandardizedAddress] serves as an example.
 */
interface StandardizedAddress: Serializable {
    override fun toString(): String
}