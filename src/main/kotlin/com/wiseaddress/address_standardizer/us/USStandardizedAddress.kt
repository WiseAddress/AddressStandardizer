package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.StandardizedAddress

enum class USAddressType { STREET, PO_BOX, RURAL_ROUTE, HIGHWAY_CONTRACT, MILITARY }

/** Parsed components; an empty optional field means it was not supplied. */
data class USStandardizedAddress @JvmOverloads constructor(
    val primaryRange: String,
    val preDirection: String,
    val streetName: String,
    val suffix: String,
    val postDirection: String,
    val secondaryIdentifier: String,
    val secondaryRange: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val addressType: USAddressType = USAddressType.STREET,
    val routeNumber: String = "",
    val boxNumber: String = "",
    val militaryUnit: String = "",
) : StandardizedAddress {
    init {
        require(state.isNotEmpty() || postalCode.isNotEmpty()) { "State or ZIP code is required" }
        require(secondaryRange.isEmpty() || secondaryIdentifier.isNotEmpty()) { "Unit range requires an identifier" }
        if (addressType == USAddressType.STREET) {
            require(primaryRange.isNotEmpty() && streetName.isNotEmpty()) { "Street address requires a number and name" }
            require(routeNumber.isEmpty() && boxNumber.isEmpty() && militaryUnit.isEmpty()) { "Street address cannot contain route/box fields" }
        } else {
            require(listOf(primaryRange, preDirection, streetName, suffix, postDirection, secondaryIdentifier, secondaryRange).all { it.isEmpty() }) {
                "Box/route address cannot contain street fields"
            }
            require(boxNumber.isNotEmpty()) { "Box number is required" }
            require((addressType == USAddressType.PO_BOX) == routeNumber.isEmpty()) { "Route/unit number is required only for route and military addresses" }
            require((addressType == USAddressType.MILITARY) == militaryUnit.isNotEmpty()) { "Military unit type is required only for military addresses" }
        }
    }

    /** Canonical single-line display. Use [formatLines] to preserve a suffixless street boundary. */
    fun format(): String = formatLines().joinToString(" ")

    fun formatLines(): List<String> {
        val delivery = when (addressType) {
            USAddressType.STREET -> joinFields(primaryRange, preDirection, streetName, suffix, postDirection, secondaryIdentifier, secondaryRange)
            USAddressType.PO_BOX -> "PO BOX $boxNumber"
            USAddressType.RURAL_ROUTE -> "RR $routeNumber BOX $boxNumber"
            USAddressType.HIGHWAY_CONTRACT -> "HC $routeNumber BOX $boxNumber"
            USAddressType.MILITARY -> "$militaryUnit $routeNumber BOX $boxNumber"
        }
        return listOf(delivery, joinFields(city, state, postalCode))
    }
}

private fun joinFields(vararg fields: String): String = fields.filter { it.isNotEmpty() }.joinToString(" ")
