package com.wiseaddress.address_standardizer

import java.io.Serializable

/** Immutable output from an address standardizer. */
interface StandardizedAddress: Serializable {
    override fun toString(): String
}
