package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.model.Model

internal data class StreetDelivery(
    val number: String,
    val preDirection: String,
    val name: String,
    val suffix: String,
    val postDirection: String,
    val unit: String,
    val unitRange: String,
    val consumed: Int,
)

/** Owns the delivery line grammar; locality and lookup loading live outside this parser. */
internal class StreetParser(private val model: Model) {
    fun beginsSecondary(tokens: List<String>, at: Int): Boolean {
        if (tokens[at] == "#") return true
        if (tokens[at] !in model.secondary_units) return false
        return tokens.getOrNull(at + 1)?.let { it == "#" || isUnitRange(it) } == true
    }

    fun parse(cursor: TokenCursor, explicitBoundary: Boolean): StreetDelivery {
        val tokens = cursor.tokens
        val end = cursor.end
        val number = houseNumber(cursor)
        if (cursor.size == 0) invalid("Street name is missing")
        highway(cursor, number, explicitBoundary)?.let { return it }

        val start = cursor.start
        val unitStart = findUnit(tokens, start, end)
        val boundedStreet = explicitBoundary || unitStart != null
        var streetEnd = unitStart ?: end
        var postDirection = ""
        val suffixIndex: Int?
        if (boundedStreet) {
            for (length in 2 downTo 1) {
                val at = streetEnd - length
                if (at <= start || (start until at).all { tokens[it] in model.directions }) continue
                val direction = direction(tokens, at, streetEnd)
                if (direction != null && direction.second == length) {
                    postDirection = direction.first
                    streetEnd = at
                    break
                }
            }
            suffixIndex = (streetEnd - 1).takeIf { it > start && tokens[it] in model.street_abbreviations }
        } else {
            var found = (start + 1 until end).firstOrNull { tokens[it] in model.street_abbreviations }
            // Pub 28 §234.2: in consecutive suffix words, the second is the suffix.
            if (found != null && found + 1 < end && tokens[found + 1] in model.street_abbreviations) found++
            suffixIndex = found
            streetEnd = found?.plus(1) ?: end
            if (found == null && streetEnd - start > 1) {
                invalid("Cannot locate the street/city boundary; use a comma or newline after a suffixless street")
            }
        }

        val nameEnd = suffixIndex ?: streetEnd
        val pre = direction(tokens, start, nameEnd)?.takeIf { start + it.second < nameEnd }
        val nameStart = start + (pre?.second ?: 0)
        if (nameStart == nameEnd || (nameStart until nameEnd).any { index ->
                val word = tokens[index]
                word == "#" || word == "/" || word.none { it.isLetterOrDigit() }
            }) {
            invalid("Street name is missing or malformed")
        }
        cursor.start = if (boundedStreet) unitStart ?: end else streetEnd
        if (!boundedStreet) postDirection = takePostDirection(cursor, false)
        val (unit, range) = secondary(cursor)
        if (explicitBoundary && cursor.size != 0) invalid("Unexpected words after the secondary unit: ${cursor.text()}")
        return StreetDelivery(
            number, pre?.first ?: "", tokens.text(nameStart, nameEnd), suffixIndex?.let { model.street_abbreviations.getValue(tokens[it]) } ?: "",
            postDirection, unit, range, cursor.start,
        )
    }

    private fun houseNumber(tokens: TokenCursor): String {
        val first = tokens.first() ?: invalid("House number is missing")
        if (validFraction(first)) { tokens.start++; return first }
        if (!first.isAsciiDigits() && !HOUSE_NUMBER.matches(first)) invalid("Invalid house number '$first'")
        tokens.start++
        if (tokens.first() == "/") {
            val denominator = tokens.first(1) ?: invalid("Fraction denominator is missing")
            val fraction = "$first/$denominator"
            if (!validFraction(fraction)) invalid("Invalid house-number fraction '$fraction'")
            tokens.start += 2
            return fraction
        }
        val next = tokens.first() ?: return first
        if ('/' in next) {
            if (!validFraction(next)) invalid("Invalid house-number fraction '$next'")
            tokens.start++
            return "$first $next"
        }
        if (next.isAsciiDigits() && tokens.first(1) == "/") {
            val fraction = "$next/${tokens.first(2) ?: invalid("Fraction denominator is missing")}"
            if (!validFraction(fraction)) invalid("Invalid house-number fraction '$fraction'")
            tokens.start += 3
            return "$first $fraction"
        }
        return first
    }

    private fun validFraction(value: String): Boolean {
        val slash = value.indexOf('/')
        return slash > 0 && value.isAsciiDigits(0, slash) && value.isAsciiDigits(slash + 1) &&
            (slash + 1 until value.length).any { value[it] != '0' }
    }

    private fun highway(cursor: TokenCursor, number: String, explicit: Boolean): StreetDelivery? {
        val initial = cursor.start
        val pre = direction(cursor.tokens, initial, cursor.end)
        val starts = if (pre == null) listOf(initial) else listOf(initial + pre.second, initial)
        for (start in starts) {
            if (start >= cursor.end) continue
            val first = cursor.tokens[start]
            for (length in model.highwayWordCountsByFirstToken[first].orEmpty()) {
                if (start + length >= cursor.end) continue
                val highway = model.csl[if (length == 1) first else cursor.text(start, start + length)] ?: continue
                val route = cursor.tokens[start + length]
                if (!ROUTE_NUMBER.matches(route)) continue
                cursor.start = start + length + 1
                return highwayResult(cursor, number, if (start == initial) "" else pre!!.first, "$highway $route", explicit)
            }
            val compact = first
            val dash = compact.indexOf('-')
            if (dash > 0) {
                val highway = model.csl[compact.substring(0, dash)] ?: continue
                val route = compact.substring(dash + 1)
                if (!ROUTE_NUMBER.matches(route)) continue
                cursor.start = start + 1
                return highwayResult(cursor, number, if (start == initial) "" else pre!!.first, "$highway $route", explicit)
            }
        }
        return null
    }

    private fun highwayResult(cursor: TokenCursor, number: String, pre: String, name: String, explicit: Boolean): StreetDelivery {
        val post = takePostDirection(cursor, explicit)
        val (unit, range) = secondary(cursor)
        if (explicit && cursor.size != 0) invalid("Unexpected words after highway number: ${cursor.text()}")
        return StreetDelivery(number, pre, name, "", post, unit, range, cursor.start)
    }

    private fun findUnit(tokens: List<String>, start: Int, end: Int): Int? {
        var lastStreetWord = end - 1
        while (lastStreetWord > start && tokens[lastStreetWord] in model.directions) lastStreetWord--
        val terminalSuffix = lastStreetWord.takeIf { tokens[it] in model.street_abbreviations }
        return (start + 1 until end).firstOrNull { index ->
            if (!isUnit(tokens[index])) return@firstOrNull false
            val next = if (index + 1 < end) tokens[index + 1] else null
            val numbered = next == "#" || next?.let(::isUnitRange) == true
            // FRONT, SIDE, and UNIT may be words inside a street name before its suffix.
            if (!numbered && terminalSuffix != null && index <= terminalSuffix) return@firstOrNull false
            numbered || next == null || model.secondary_units[tokens[index]] !in model.secondary_range_unneeded ||
                (start + 1 until index).any { tokens[it] in model.street_abbreviations }
        }
    }

    private fun secondary(cursor: TokenCursor): Pair<String, String> {
        val token = cursor.first() ?: return "" to ""
        val identifier = if (token == "#") "#" else model.secondary_units[token] ?: return "" to ""
        cursor.start++
        val numbered = cursor.first() == "#"
        if (numbered && identifier == "#") invalid("Repeated secondary unit marker '#'")
        if (numbered) cursor.start++
        val range = cursor.first()
        if (range != null && isUnitRange(range)) { cursor.start++; return identifier to range }
        if (!numbered && identifier in model.secondary_range_unneeded) return identifier to ""
        invalid("Secondary identifier '$identifier' requires a valid unit number")
    }

    private fun isUnit(token: String): Boolean = token == "#" || token in model.secondary_units

    private fun isUnitRange(value: String): Boolean {
        var hasDigit = false
        var previousHyphen = true
        for (char in value) {
            when (char) {
                in '0'..'9' -> { hasDigit = true; previousHyphen = false }
                in 'A'..'Z' -> previousHyphen = false
                '-' -> { if (previousHyphen) return false; previousHyphen = true }
                else -> return false
            }
        }
        return !previousHyphen && (hasDigit || value.length == 1)
    }

    private fun takePostDirection(cursor: TokenCursor, explicit: Boolean): String {
        val found = direction(cursor.tokens, cursor.start, cursor.end) ?: return ""
        val after = cursor.start + found.second
        // A full direction before a city (WEST SPRINGFIELD) needs a delivery boundary to disambiguate.
        val abbreviated = cursor.first() == found.first
        if (!explicit && !abbreviated && after != cursor.end && !isUnit(cursor.tokens[after])) return ""
        cursor.start = after
        return found.first
    }

    private fun direction(tokens: List<String>, start: Int, end: Int): Pair<String, Int>? {
        if (start >= end) return null
        val first = model.directions[tokens[start]] ?: return null
        if (start + 1 < end) {
            val second = model.directions[tokens[start + 1]]
            if (first in CARDINALS && second in CARDINALS) {
                val combined = first + second
                if (combined in model.directions) return combined to 2
            }
        }
        return first to 1
    }

    private companion object {
        val HOUSE_NUMBER = Regex("[0-9]+(?:-[0-9]+)?(?:-?[A-Z])?")
        val ROUTE_NUMBER = Regex("[0-9]+(?:-?[A-Z])?(?:-[0-9]+[A-Z]?)?")
        val CARDINALS = setOf("N", "S", "E", "W")
    }
}
