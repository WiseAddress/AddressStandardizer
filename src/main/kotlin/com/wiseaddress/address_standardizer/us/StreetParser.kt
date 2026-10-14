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
    val privateMailbox: String = "",
)

/** Owns the delivery line grammar; locality and lookup loading live outside this parser. */
internal class StreetParser(private val model: Model, private val secondaries: SecondaryParser = SecondaryParser(model)) {
    fun beginsSecondary(tokens: List<String>, at: Int, end: Int = tokens.size): Boolean = secondaries.begins(tokens, at, end)

    fun beginsDelivery(tokens: List<String>, start: Int, end: Int): Boolean {
        if (start + 2 >= end) return false
        val primary = tokens[start]
        if (primary.none { it in '0'..'9' }) return false
        if (!primary.isAsciiDigits() && !validFraction(primary) && !HOUSE_NUMBER.matches(primary)) return false
        // Without a suffix, a numeric city name and a second street cannot be separated reliably.
        return (start + 2 until end).any { tokens[it] in model.street_abbreviations }
    }

    fun parse(cursor: TokenCursor, explicitBoundary: Boolean, preserveStreetWords: Boolean = false): StreetDelivery {
        val tokens = cursor.tokens
        val end = cursor.end
        val number = houseNumber(cursor)
        if (cursor.size == 0) invalid("Street name is missing")
        if (preserveStreetWords && cursor.first() in PUERTO_RICO_STREET_TYPES) {
            return preservedStreet(cursor, number, explicitBoundary)
        }
        highway(cursor, number, explicitBoundary, preserveStreetWords)?.let { return it }

        val start = cursor.start
        val unitStart = findUnit(tokens, start, end)
        val boundedStreet = explicitBoundary || unitStart != null
        var streetEnd = unitStart ?: end
        val alphabeticStreet = streetEnd == start + 2 && tokens[start] in model.street_abbreviations &&
            tokens[start + 1].length == 1 && tokens[start + 1][0] in 'A'..'Z'
        var postDirection = ""
        var postName = ""
        val suffixIndex: Int?
        if (boundedStreet) {
            if (!preserveStreetWords && streetEnd > start + 2) {
                val first = model.directions[tokens[streetEnd - 2]]
                val second = model.directions[tokens[streetEnd - 1]]
                if (first != null && second != null && OPPOSITES[first] == second &&
                    !(start until streetEnd - 2).all { tokens[it] in model.directions }) {
                    postDirection = first
                    postName = DIRECTION_NAMES.getValue(second)
                    streetEnd -= 2
                }
            }
            for (length in 2 downTo 1) {
                val at = streetEnd - length
                if (postName.isNotEmpty() || preserveStreetWords || alphabeticStreet || at <= start ||
                    (start until at).all { tokens[it] in model.directions }) continue
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
        val pre = if (preserveStreetWords) null else direction(tokens, start, nameEnd)?.takeIf { start + it.second < nameEnd }
        val nameStart = start + (pre?.second ?: 0)
        validateName(tokens, nameStart, nameEnd)
        cursor.start = if (boundedStreet) unitStart ?: end else streetEnd
        if (!boundedStreet && !preserveStreetWords) postDirection = takePostDirection(cursor, false)
        val secondary = secondaries.parse(cursor, explicitBoundary)
        if (explicitBoundary && cursor.size != 0) invalid("Unexpected words after the secondary unit: ${cursor.text()}")
        val name = streetName(tokens, nameStart, nameEnd, pre?.first, suffixIndex != null, alphabeticStreet)
        return StreetDelivery(
            number, pre?.first ?: "", if (postName.isEmpty()) name else "$name $postName",
            suffixIndex?.let { model.street_abbreviations.getValue(tokens[it]) } ?: "",
            postDirection, secondary.unit, secondary.range, cursor.start, secondary.privateMailbox,
        )
    }

    private fun preservedStreet(cursor: TokenCursor, number: String, explicit: Boolean): StreetDelivery {
        val start = cursor.start
        if (cursor.size < 2) invalid("Puerto Rico street type requires a street name")
        val firstName = cursor.tokens[start + 1]
        if (firstName in model.secondary_units && model.secondary_units[firstName] !in model.secondary_range_unneeded &&
            beginsSecondary(cursor.tokens, start + 1, cursor.end)) invalid("Street name is missing before the secondary unit")
        val unitStart = findUnit(cursor.tokens, start + 1, cursor.end)
        if (!explicit && unitStart == null) invalid("Use a comma or newline after a Puerto Rico street name")
        val streetEnd = unitStart ?: cursor.end
        validateName(cursor.tokens, start, streetEnd)
        val name = cursor.text(start, streetEnd)
        cursor.start = streetEnd
        val secondary = secondaries.parse(cursor, explicit)
        if (explicit && cursor.size != 0) invalid("Unexpected words after the secondary unit: ${cursor.text()}")
        return StreetDelivery(number, "", name, "", "", secondary.unit, secondary.range, cursor.start, secondary.privateMailbox)
    }

    private fun validateName(tokens: List<String>, start: Int, end: Int) {
        if (start == end || (start until end).any { index ->
                val word = tokens[index]
                word == "#" || word == "/" || word.none { it.isLetterOrDigit() }
            }) invalid("Street name is missing or malformed")
        if ((start until end).any { beginsSpecialDelivery(tokens, it, end) }) {
            invalid("Separate multiple delivery addresses onto distinct lines")
        }
    }

    private fun streetName(tokens: List<String>, start: Int, end: Int, pre: String?, hasSuffix: Boolean, alphabeticStreet: Boolean): String {
        val first = tokens[start]
        val opposing = pre?.let { OPPOSITES[it] }?.takeIf { it == model.directions[first] }
        val head = if (opposing != null) DIRECTION_NAMES.getValue(opposing)
            else if (alphabeticStreet) model.streetFullNames[first] ?: first else first
        val last = tokens[end - 1]
        val tail = if (hasSuffix) model.streetFullNames[last] ?: last else last
        if (head == first && tail == last) return tokens.text(start, end)
        if (start + 1 == end) return if (head != first) head else tail
        return buildString {
            append(head)
            for (index in start + 1 until end) append(' ').append(if (index == end - 1) tail else tokens[index])
        }
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

    private fun highway(cursor: TokenCursor, number: String, explicit: Boolean, preserveDirections: Boolean): StreetDelivery? {
        val initial = cursor.start
        val pre = if (preserveDirections) null else direction(cursor.tokens, initial, cursor.end)
        val starts = if (pre == null) listOf(initial) else listOf(initial + pre.second, initial)
        for (start in starts) {
            if (start >= cursor.end) continue
            val first = cursor.tokens[start]
            for (length in model.highwayWordCountsByFirstToken[first].orEmpty()) {
                if (start + length >= cursor.end) continue
                val highway = model.csl[if (length == 1) first else cursor.text(start, start + length)] ?: continue
                val route = cursor.tokens[start + length]
                if (!validRoute(highway, route)) continue
                cursor.start = start + length + 1
                // Appendix F uses SR for STATE ROAD with numeric routes and STATE ROUTE with letter routes.
                val name = highwayName(highway, first, route)
                return highwayResult(cursor, number, if (start == initial) "" else pre!!.first, "$name $route", explicit, preserveDirections)
            }
            val compact = first
            val dash = compact.indexOf('-')
            if (dash > 0) {
                val highway = model.csl[compact.substring(0, dash)] ?: continue
                val route = compact.substring(dash + 1)
                if (!validRoute(highway, route)) continue
                cursor.start = start + 1
                val name = highwayName(highway, compact.substring(0, dash), route)
                return highwayResult(cursor, number, if (start == initial) "" else pre!!.first, "$name $route", explicit, preserveDirections)
            }
            val digit = compact.indexOfFirst { it in '0'..'9' }
            if (digit > 0) {
                val highway = model.csl[compact.substring(0, digit)] ?: continue
                val route = compact.substring(digit)
                if (!validRoute(highway, route)) continue
                cursor.start = start + 1
                return highwayResult(cursor, number, if (start == initial) "" else pre!!.first, "$highway $route", explicit, preserveDirections)
            }
        }
        return null
    }

    private fun validRoute(highway: String, route: String): Boolean = ROUTE_NUMBER.matches(route) &&
        (route[0] in '0'..'9' || (highway !in NUMERIC_HIGHWAYS && route !in model.street_abbreviations && route !in model.secondary_units))

    private fun highwayName(highway: String, alias: String, route: String): String =
        if (alias == "SR" && highway == "STATE ROAD" && route.all { it in 'A'..'Z' }) "STATE ROUTE" else highway

    private fun highwayResult(cursor: TokenCursor, number: String, pre: String, name: String, explicit: Boolean, preserveDirections: Boolean): StreetDelivery {
        var streetName = name
        var suffix = ""
        if (explicit && cursor.first() == "FRONTAGE" && cursor.first(1) in model.street_abbreviations) {
            streetName += " FRONTAGE"
            cursor.start++
        }
        val first = cursor.first()
        if (explicit && first != null && first in model.street_abbreviations && !secondaries.begins(cursor.tokens, cursor.start, cursor.end, true)) {
            val second = cursor.first(1)
            if (second != null && second in model.street_abbreviations && !secondaries.begins(cursor.tokens, cursor.start + 1, cursor.end, true)) {
                streetName += " ${model.streetFullNames[first] ?: first}"
                cursor.start++
            }
            suffix = model.street_abbreviations.getValue(cursor.first()!!)
            cursor.start++
        }
        val post = if (preserveDirections) "" else takePostDirection(cursor, explicit)
        val next = cursor.first()
        val ambiguousCity = !explicit && next in model.street_abbreviations && next in model.secondary_units &&
            !beginsSecondary(cursor.tokens, cursor.start, cursor.end)
        val secondary = if (ambiguousCity) SecondaryDelivery.EMPTY else secondaries.parse(cursor, explicit)
        if (explicit && cursor.size != 0) invalid("Unexpected words after highway number: ${cursor.text()}")
        return StreetDelivery(number, pre, streetName, suffix, post, secondary.unit, secondary.range, cursor.start, secondary.privateMailbox)
    }

    private fun findUnit(tokens: List<String>, start: Int, end: Int): Int? {
        var lastStreetWord = end - 1
        while (lastStreetWord > start && tokens[lastStreetWord] in model.directions) lastStreetWord--
        val terminalSuffix = lastStreetWord.takeIf { tokens[it] in model.street_abbreviations }
        return (start + 1 until end).firstOrNull { index ->
            if (!isUnit(tokens[index])) return@firstOrNull false
            val next = if (index + 1 < end) tokens[index + 1] else null
            val numbered = next == "#" || next?.let(secondaries::isConfidentRange) == true
            // FRONT, SIDE, and UNIT may be words inside a street name before its suffix.
            if (!numbered && terminalSuffix != null && index <= terminalSuffix) return@firstOrNull false
            numbered || next == null || model.secondary_units[tokens[index]] !in model.secondary_range_unneeded ||
                (start + 1 until index).any { tokens[it] in model.street_abbreviations }
        }
    }

    private fun isUnit(token: String): Boolean = secondaries.isDesignator(token)

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
        val HOUSE_NUMBER = Regex("[A-Z]*[0-9]+[A-Z0-9]*(?:-[A-Z0-9]+)*")
        val ROUTE_NUMBER = Regex("[0-9]+(?:-?[A-Z]+)?(?:-[0-9]+[A-Z]*)?|[A-Z]{1,2}")
        val CARDINALS = setOf("N", "S", "E", "W")
        val OPPOSITES = mapOf("N" to "S", "S" to "N", "E" to "W", "W" to "E")
        val DIRECTION_NAMES = mapOf("N" to "NORTH", "S" to "SOUTH", "E" to "EAST", "W" to "WEST")
        val PUERTO_RICO_STREET_TYPES = setOf("CALLE", "AVENIDA", "AVE")
        val NUMERIC_HIGHWAYS = setOf("INTERSTATE", "US HIGHWAY")
    }
}
