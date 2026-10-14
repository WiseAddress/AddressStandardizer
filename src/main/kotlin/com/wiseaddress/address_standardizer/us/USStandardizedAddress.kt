package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.StandardizedAddress

enum class USAddressType { STREET, PO_BOX, RURAL_ROUTE, HIGHWAY_CONTRACT, MILITARY, GENERAL_DELIVERY }

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
    val urbanization: String = "",
    val privateMailbox: String = "",
) : StandardizedAddress {
    init {
        require(state.isNotEmpty() || postalCode.isNotEmpty()) { "State or ZIP code is required" }
        require(privateMailbox.isEmpty() || (addressType != USAddressType.MILITARY && addressType != USAddressType.GENERAL_DELIVERY)) { "Private mailbox requires a physical street, route, or PO box" }
        require(secondaryRange.isEmpty() || secondaryIdentifier.isNotEmpty()) { "Unit range requires an identifier" }
        require(urbanization.isEmpty() || state == "PR" && addressType == USAddressType.STREET) { "Urbanization requires a Puerto Rico street address" }
        if (addressType == USAddressType.STREET) {
            require(primaryRange.isNotEmpty() && streetName.isNotEmpty()) { "Street address requires a number and name" }
            require(routeNumber.isEmpty() && boxNumber.isEmpty() && militaryUnit.isEmpty()) { "Street address cannot contain route/box fields" }
        } else {
            require(listOf(primaryRange, preDirection, streetName, suffix, postDirection, secondaryIdentifier, secondaryRange).all { it.isEmpty() }) {
                "Special delivery address cannot contain street fields"
            }
            require((addressType == USAddressType.GENERAL_DELIVERY) == boxNumber.isEmpty()) { "Box number is required except for general delivery" }
            val requiresRoute = addressType == USAddressType.RURAL_ROUTE || addressType == USAddressType.HIGHWAY_CONTRACT || addressType == USAddressType.MILITARY
            require(requiresRoute == routeNumber.isNotEmpty()) { "Route/unit number is required only for route and military addresses" }
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
            USAddressType.GENERAL_DELIVERY -> "GENERAL DELIVERY"
        }
        val deliveryLine = if (privateMailbox.isEmpty()) delivery else "$delivery PMB $privateMailbox"
        val locality = joinFields(city, state, postalCode)
        return if (urbanization.isEmpty()) listOf(deliveryLine, locality) else listOf("URB $urbanization", deliveryLine, locality)
    }
}

private fun joinFields(vararg fields: String): String = fields.filter { it.isNotEmpty() }.joinToString(" ")
