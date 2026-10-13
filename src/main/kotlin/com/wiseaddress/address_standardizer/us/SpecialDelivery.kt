package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.exception.InvalidAddressException

internal data class SpecialDelivery(
    val type: USAddressType,
    val routeNumber: String = "",
    val boxNumber: String = "",
    val militaryUnit: String = "",
    val consumed: Int,
)

private val number = Regex("[0-9]+")
private val highwayNumber = Regex("[0-9]+[A-Z]?")
private val routeBoxNumber = Regex("[0-9]+[A-Z]?(?:-[0-9]+[A-Z]?)?")
private val postOfficeBoxNumber = Regex("[0-9]+|[A-Z]")
private val compactRoute = Regex("(RR|RFD|RD|HC)([0-9]+[A-Z]?)")
private val leadingRouteZeros = Regex("^0+(?=[0-9])")

internal fun parseSpecialDelivery(tokens: List<String>): SpecialDelivery? {
    val first = tokens.firstOrNull() ?: return null
    if (first.isNotEmpty() && first[0] in '0'..'9') return null
    return when (first) {
        "PO" -> {
            expectToken(tokens, 1, "BOX")
            parsePostOfficeBox(tokens, 2)
        }
        "P" -> {
            if (tokens.getOrNull(1) != "O") return null
            expectToken(tokens, 2, "BOX")
            parsePostOfficeBox(tokens, 3)
        }
        "POST" -> {
            if (tokens.getOrNull(1) != "OFFICE") return null
            expectToken(tokens, 2, "BOX")
            parsePostOfficeBox(tokens, 3)
        }
        "CALLER", "BIN", "LOCKBOX", "DRAWER" -> parsePostOfficeBox(tokens, 1)
        "FIRM" -> {
            if (tokens.getOrNull(1) != "CALLER") return null
            parsePostOfficeBox(tokens, 2)
        }
        "RR", "RFD", "RD" -> parseRouteBox(tokens, afterOptionalRoute(tokens, 1), USAddressType.RURAL_ROUTE)
        "RURAL" -> {
            expectToken(tokens, 1, "ROUTE")
            parseRouteBox(tokens, 2, USAddressType.RURAL_ROUTE)
        }
        "HC" -> parseRouteBox(tokens, afterOptionalRoute(tokens, 1), USAddressType.HIGHWAY_CONTRACT)
        "HIGHWAY" -> {
            if (tokens.getOrNull(1) != "CONTRACT") return null
            parseRouteBox(tokens, afterOptionalRoute(tokens, 2), USAddressType.HIGHWAY_CONTRACT)
        }
        "STAR" -> {
            if (tokens.getOrNull(1) != "ROUTE") return null
            parseRouteBox(tokens, 2, USAddressType.HIGHWAY_CONTRACT)
        }
        "CPR", "OPC", "PSC", "UPR", "UNIT", "CMR" -> {
            // USPS renamed the CMR designation to CPR in January 2023.
            val unit = if (first == "CMR") "CPR" else first
            parseRouteBox(tokens, 1, USAddressType.MILITARY, unit)
        }
        else -> {
            val compact = compactRoute.matchEntire(first) ?: return null
            val type = if (compact.groupValues[1] == "HC") USAddressType.HIGHWAY_CONTRACT else USAddressType.RURAL_ROUTE
            parseRouteBox(tokens, 1, type, inlineNumber = compact.groupValues[2])
        }
    }
}

private fun parsePostOfficeBox(tokens: List<String>, start: Int): SpecialDelivery {
    val index = if (tokens.getOrNull(start) == "#") start + 1 else start
    val box = tokens.getOrNull(index)?.removePrefix("#")
    if (box == null || !postOfficeBoxNumber.matches(box)) {
        throw InvalidAddressException("PO BOX requires a numeric box number or a single letter.")
    }
    return SpecialDelivery(USAddressType.PO_BOX, boxNumber = box, consumed = index + 1)
}

private fun parseRouteBox(
    tokens: List<String>,
    start: Int,
    type: USAddressType,
    militaryUnit: String = "",
    inlineNumber: String? = null,
): SpecialDelivery {
    var index = start
    if (inlineNumber == null) {
        when (tokens.getOrNull(index)) {
            "NUMBER", "NO", "#" -> index++
        }
    }
    val route = inlineNumber ?: tokens.getOrNull(index++)?.removePrefix("#")
    val routePattern = if (type == USAddressType.HIGHWAY_CONTRACT) highwayNumber else number
    if (route == null || !routePattern.matches(route)) {
        throw InvalidAddressException("${type.name} requires a valid route or unit number.")
    }
    val marker = tokens.getOrNull(index)
    if (marker == "BOX") {
        index++
    } else if (type == USAddressType.MILITARY || marker?.startsWith("#") != true) {
        throw InvalidAddressException("${type.name} requires BOX and a box number.")
    }
    if (tokens.getOrNull(index) == "#") index++
    val box = tokens.getOrNull(index)?.removePrefix("#")
    val boxPattern = if (type == USAddressType.MILITARY) number else routeBoxNumber
    if (box == null || !boxPattern.matches(box)) {
        throw InvalidAddressException("${type.name} requires a valid box number.")
    }
    val normalizedRoute = if (type == USAddressType.MILITARY) route else route.replace(leadingRouteZeros, "")
    return SpecialDelivery(type, normalizedRoute, box, militaryUnit, index + 1)
}

private fun afterOptionalRoute(tokens: List<String>, index: Int): Int =
    if (tokens.getOrNull(index) == "ROUTE") index + 1 else index

private fun expectToken(tokens: List<String>, index: Int, expected: String) {
    if (tokens.getOrNull(index) != expected) {
        throw InvalidAddressException("Expected $expected in special delivery address.")
    }
}
