package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.exception.InvalidAddressException

internal data class SpecialDelivery(
    val type: USAddressType,
    val routeNumber: String = "",
    val boxNumber: String = "",
    val militaryUnit: String = "",
    val consumed: Int,
    val privateMailbox: String = "",
)

private val compactRoute = Regex("(RR|RFD|RD|HC)([0-9]+[A-Z]?)")

/** Complete delivery prefixes distinguish military UNIT addresses from ordinary secondary units. */
internal fun beginsSpecialDelivery(tokens: List<String>, at: Int, end: Int = tokens.size): Boolean {
    if (at >= end) return false
    fun token(index: Int): String? = if (index < end) tokens.getOrNull(index) else null
    fun postOfficeBox(start: Int): Boolean {
        val index = if (token(start) == "#") start + 1 else start
        return token(index)?.removePrefix("#")?.isPostOfficeBoxNumber() == true
    }
    fun routeBox(start: Int, type: USAddressType, inlineNumber: String? = null): Boolean {
        var index = if (inlineNumber == null) afterNumberMarker(tokens, start) else start
        val route = inlineNumber ?: token(index++)?.removePrefix("#") ?: return false
        if (!(if (type == USAddressType.HIGHWAY_CONTRACT) route.isNumberWithOptionalLetter() else route.isAsciiDigits())) return false
        val marker = token(index)
        if (marker == "BOX" || type != USAddressType.MILITARY && (marker == "BUZON" || marker == "BZN")) index++
        else if (type == USAddressType.MILITARY || marker?.startsWith("#") != true) return false
        index = afterNumberMarker(tokens, index)
        val box = token(index)?.removePrefix("#") ?: return false
        return if (type == USAddressType.MILITARY) box.isAsciiDigits() else box.isRouteBoxNumber(allowAlphabetic = true)
    }
    val first = tokens[at]
    return when (first) {
        "PO", "GPO" -> token(at + 1) == "BOX" && postOfficeBox(at + 2)
        "P" -> token(at + 1) == "O" && token(at + 2) == "BOX" && postOfficeBox(at + 3)
        "POST" -> token(at + 1) == "OFFICE" && token(at + 2) == "BOX" && postOfficeBox(at + 3)
        "CALLER", "BIN", "LOCKBOX", "DRAWER", "APARTADO", "APTDO" -> postOfficeBox(at + 1)
        "FIRM" -> token(at + 1) == "CALLER" && postOfficeBox(at + 2)
        "CALL" -> token(at + 1) == "BOX" && postOfficeBox(at + 2)
        "RR", "RFD", "RD", "RT", "RURAL" -> routeBox(afterOptionalRoute(tokens, at + 1), USAddressType.RURAL_ROUTE)
        "HC" -> routeBox(afterOptionalRoute(tokens, at + 1), USAddressType.HIGHWAY_CONTRACT)
        "HIGHWAY" -> token(at + 1) == "CONTRACT" && routeBox(afterOptionalRoute(tokens, at + 2), USAddressType.HIGHWAY_CONTRACT)
        "STAR" -> token(at + 1) == "ROUTE" && routeBox(at + 2, USAddressType.HIGHWAY_CONTRACT)
        "RUTA" -> when (token(at + 1)) {
            "RURAL" -> routeBox(at + 2, USAddressType.RURAL_ROUTE)
            "ESTRELLA" -> routeBox(at + 2, USAddressType.HIGHWAY_CONTRACT)
            else -> false
        }
        "CPR", "OPC", "PSC", "UPR", "UNIT", "CMR" -> routeBox(at + 1, USAddressType.MILITARY)
        "GENERAL" -> token(at + 1) == "DELIVERY"
        "ENTREGA" -> token(at + 1) == "GENERAL"
        else -> {
            if (!first.startsWith("R") && !first.startsWith("HC")) return false
            val compact = compactRoute.matchEntire(first) ?: return false
            val type = if (compact.groupValues[1] == "HC") USAddressType.HIGHWAY_CONTRACT else USAddressType.RURAL_ROUTE
            routeBox(at + 1, type, compact.groupValues[2])
        }
    }
}

internal fun parseSpecialDelivery(tokens: List<String>, state: String = "", explicitBoundary: Boolean = false): SpecialDelivery? {
    val first = tokens.firstOrNull() ?: return null
    if (first.isNotEmpty() && first[0] in '0'..'9') return null
    if (state == "PR") parsePuertoRicoDelivery(tokens, explicitBoundary)?.let { return it.withPrivateMailbox(tokens, explicitBoundary) }
    return when (first) {
        "GENERAL" -> {
            expectToken(tokens, 1, "DELIVERY")
            SpecialDelivery(USAddressType.GENERAL_DELIVERY, consumed = 2)
        }
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
        "RR", "RFD", "RD" -> parseRouteBox(tokens, afterOptionalRoute(tokens, 1), USAddressType.RURAL_ROUTE, explicitBoundary = explicitBoundary)
        "RURAL" -> {
            expectToken(tokens, 1, "ROUTE")
            parseRouteBox(tokens, 2, USAddressType.RURAL_ROUTE, explicitBoundary = explicitBoundary)
        }
        "HC" -> parseRouteBox(tokens, afterOptionalRoute(tokens, 1), USAddressType.HIGHWAY_CONTRACT, explicitBoundary = explicitBoundary)
        "HIGHWAY" -> {
            if (tokens.getOrNull(1) != "CONTRACT") return null
            parseRouteBox(tokens, afterOptionalRoute(tokens, 2), USAddressType.HIGHWAY_CONTRACT, explicitBoundary = explicitBoundary)
        }
        "STAR" -> {
            if (tokens.getOrNull(1) != "ROUTE") return null
            parseRouteBox(tokens, 2, USAddressType.HIGHWAY_CONTRACT, explicitBoundary = explicitBoundary)
        }
        "CPR", "OPC", "PSC", "UPR", "UNIT", "CMR" -> {
            // USPS renamed the CMR designation to CPR in January 2023.
            val unit = if (first == "CMR") "CPR" else first
            parseRouteBox(tokens, 1, USAddressType.MILITARY, unit, explicitBoundary = explicitBoundary)
        }
        else -> {
            val compact = compactRoute.matchEntire(first) ?: return null
            val type = if (compact.groupValues[1] == "HC") USAddressType.HIGHWAY_CONTRACT else USAddressType.RURAL_ROUTE
            parseRouteBox(tokens, 1, type, inlineNumber = compact.groupValues[2], explicitBoundary = explicitBoundary)
        }
    }.withPrivateMailbox(tokens, explicitBoundary)
}

private fun SpecialDelivery.withPrivateMailbox(tokens: List<String>, explicitBoundary: Boolean): SpecialDelivery {
    if (type == USAddressType.GENERAL_DELIVERY || type == USAddressType.MILITARY || tokens.getOrNull(consumed) != "PMB") return this
    val cursor = TokenCursor(tokens, start = consumed)
    val mailbox = parsePrivateMailbox(cursor, explicitBoundary)
    return copy(consumed = cursor.start, privateMailbox = mailbox)
}

private fun parsePuertoRicoDelivery(tokens: List<String>, explicitBoundary: Boolean): SpecialDelivery? = when (tokens[0]) {
    "APTDO", "APARTADO", "BOX", "BUZON" -> parsePostOfficeBox(tokens, 1)
    "CALL", "GPO" -> {
        expectToken(tokens, 1, "BOX")
        parsePostOfficeBox(tokens, 2)
    }
    "RT", "RURAL" -> parseRouteBox(tokens, afterOptionalRoute(tokens, 1), USAddressType.RURAL_ROUTE, spanishBox = true, explicitBoundary = explicitBoundary)
    "RUTA" -> {
        val type = when (tokens.getOrNull(1)) {
            "RURAL" -> USAddressType.RURAL_ROUTE
            "ESTRELLA" -> USAddressType.HIGHWAY_CONTRACT
            else -> throw InvalidAddressException("Expected RURAL or ESTRELLA after RUTA.")
        }
        parseRouteBox(tokens, 2, type, spanishBox = true, explicitBoundary = explicitBoundary)
    }
    "RR", "RFD", "RD" -> parseRouteBox(tokens, afterOptionalRoute(tokens, 1), USAddressType.RURAL_ROUTE, spanishBox = true, explicitBoundary = explicitBoundary)
    "ENTREGA" -> {
        expectToken(tokens, 1, "GENERAL")
        SpecialDelivery(USAddressType.GENERAL_DELIVERY, consumed = 2)
    }
    else -> null
}

private fun parsePostOfficeBox(tokens: List<String>, start: Int): SpecialDelivery {
    val index = if (tokens.getOrNull(start) == "#") start + 1 else start
    val box = tokens.getOrNull(index)?.removePrefix("#")
    if (box == null || !box.isPostOfficeBoxNumber()) {
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
    spanishBox: Boolean = false,
    explicitBoundary: Boolean = false,
): SpecialDelivery {
    var index = if (inlineNumber == null) afterNumberMarker(tokens, start) else start
    val route = inlineNumber ?: tokens.getOrNull(index++)?.removePrefix("#")
    if (route == null || !(if (type == USAddressType.HIGHWAY_CONTRACT) route.isNumberWithOptionalLetter() else route.isAsciiDigits())) {
        throw InvalidAddressException("${type.name} requires a valid route or unit number.")
    }
    val marker = tokens.getOrNull(index)
    if (marker == "BOX" || spanishBox && (marker == "BUZON" || marker == "BZN")) {
        index++
    } else if (type == USAddressType.MILITARY || marker?.startsWith("#") != true) {
        throw InvalidAddressException("${type.name} requires BOX and a box number.")
    }
    index = afterNumberMarker(tokens, index)
    val box = tokens.getOrNull(index)?.removePrefix("#")
    if (box == null || !(if (type == USAddressType.MILITARY) box.isAsciiDigits() else box.isRouteBoxNumber(allowAlphabetic = explicitBoundary))) {
        throw InvalidAddressException("${type.name} requires a valid box number.")
    }
    val normalizedRoute = if (type == USAddressType.MILITARY) route else route.withoutLeadingRouteZeros()
    return SpecialDelivery(type, normalizedRoute, box, militaryUnit, index + 1)
}

private fun afterNumberMarker(tokens: List<String>, index: Int): Int = when (tokens.getOrNull(index)) {
    "NUMBER", "NO", "#" -> index + 1
    else -> index
}

private fun afterOptionalRoute(tokens: List<String>, index: Int): Int =
    if (tokens.getOrNull(index) == "ROUTE") index + 1 else index

private fun expectToken(tokens: List<String>, index: Int, expected: String) {
    if (tokens.getOrNull(index) != expected) {
        throw InvalidAddressException("Expected $expected in special delivery address.")
    }
}

private fun String.isNumberWithOptionalLetter(from: Int = 0, until: Int = length): Boolean {
    val digitsEnd = if (from < until && this[until - 1] in 'A'..'Z') until - 1 else until
    return isAsciiDigits(from, digitsEnd)
}

private fun String.isPostOfficeBoxNumber(): Boolean = isAsciiDigits() || length == 1 && this[0] in 'A'..'Z'

private fun String.isRouteBoxNumber(allowAlphabetic: Boolean): Boolean = isSecondaryRange(this, allowAlphabetic)

private fun String.withoutLeadingRouteZeros(): String {
    var start = 0
    while (start + 1 < length && this[start] == '0' && this[start + 1] in '0'..'9') start++
    return if (start == 0) this else substring(start)
}
