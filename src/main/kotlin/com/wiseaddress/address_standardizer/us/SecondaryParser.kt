package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.model.Model

internal data class SecondaryDelivery(val unit: String, val range: String, val privateMailbox: String = "") {
    companion object {
        val EMPTY = SecondaryDelivery("", "")
    }
}

/** Physical units and CMRA mailboxes have separate identities even when they share a delivery line. */
internal class SecondaryParser(private val model: Model) {
    fun isDesignator(token: String): Boolean = token == "#" || token == "PMB" || token in model.secondary_units

    fun begins(tokens: List<String>, at: Int, end: Int = tokens.size, explicitBoundary: Boolean = false): Boolean {
        if (at >= end) return false
        if (tokens[at] == "#" || tokens[at] == "PMB") return true
        if (!isDesignator(tokens[at])) return false
        return at + 1 < end && tokens[at + 1].let { it == "#" || isSecondaryRange(it, explicitBoundary) }
    }

    fun isConfidentRange(value: String): Boolean = isSecondaryRange(value, allowAlphabetic = false)

    fun parse(cursor: TokenCursor, explicitBoundary: Boolean = false): SecondaryDelivery {
        val token = cursor.first() ?: return SecondaryDelivery.EMPTY
        if (token == "PMB") {
            val mailbox = parsePrivateMailbox(cursor, explicitBoundary)
            rejectAdditional(cursor, explicitBoundary)
            return SecondaryDelivery("", "", mailbox)
        }
        val identifier = if (token == "#") "#" else model.secondary_units[token] ?: return SecondaryDelivery.EMPTY
        cursor.start++
        val numbered = cursor.first() == "#"
        if (numbered && identifier == "#") invalid("Repeated secondary unit marker '#'")
        if (numbered) cursor.start++
        val next = cursor.first()
        val range = if (next != null && next != "PMB" && isSecondaryRange(next, explicitBoundary)) {
            cursor.start++
            next
        } else if (!numbered && identifier in model.secondary_range_unneeded) ""
        else invalid("Secondary identifier '$identifier' requires a valid unit number")

        if (cursor.first() == "#") invalid("Use PMB after a physical secondary unit")
        val mailbox = parsePrivateMailbox(cursor, explicitBoundary)
        rejectAdditional(cursor, explicitBoundary)
        return SecondaryDelivery(identifier, range, mailbox)
    }

    private fun rejectAdditional(cursor: TokenCursor, explicitBoundary: Boolean) {
        val next = cursor.first() ?: return
        val identifier = model.secondary_units[next]
        val requiredUnit = identifier != null && identifier !in model.secondary_range_unneeded && next !in model.street_abbreviations
        if (next == "PMB" || next == "#" ||
            requiredUnit || (explicitBoundary && isDesignator(next)) || begins(cursor.tokens, cursor.start, cursor.end)) {
            invalid("Only one physical secondary unit followed by one PMB is supported")
        }
    }
}

internal fun parsePrivateMailbox(cursor: TokenCursor, explicitBoundary: Boolean = false): String {
    if (cursor.first() != "PMB") return ""
    cursor.start++
    val range = cursor.first()
    if (range == null || range == "PMB" || !isSecondaryRange(range, explicitBoundary)) {
        invalid("PMB requires a valid private mailbox number")
    }
    cursor.start++
    if (cursor.first() == "PMB") invalid("Duplicate private mailbox designator")
    return range
}

internal fun isSecondaryRange(value: String, allowAlphabetic: Boolean): Boolean {
    var hasDigit = false
    var hasHyphen = false
    var previousHyphen = true
    for (char in value) {
        when (char) {
            in '0'..'9' -> { hasDigit = true; previousHyphen = false }
            in 'A'..'Z' -> previousHyphen = false
            '-' -> {
                if (previousHyphen) return false
                hasHyphen = true
                previousHyphen = true
            }
            else -> return false
        }
    }
    return !previousHyphen && (hasDigit || value.length == 1 || allowAlphabetic && !hasHyphen)
}
