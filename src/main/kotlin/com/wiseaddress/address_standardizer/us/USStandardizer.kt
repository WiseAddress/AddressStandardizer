package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.AddressStandardizer
import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import com.wiseaddress.address_standardizer.model.Model
import com.wiseaddress.address_standardizer.model.ModelFactory

/** Offline US address parser. Instances and their immutable lookup data can be shared across threads. */
class USStandardizer @JvmOverloads constructor(private val model: Model = ModelFactory.load("us")) : AddressStandardizer {
    private val streets = StreetParser(model)

    override fun standardize(address: String): USStandardizedAddress {
        val input = AddressInput(address)
        val tail = TokenCursor(input.tokens)
        val postalCode = postalCode(tail)
        val state = state(tail)
        if (state.isEmpty() && postalCode.isEmpty()) invalid("A valid state or ZIP code is required")
        if (tail.size == 0) invalid("Delivery address is missing")
        val localityEnd = tail.end

        val cityBoundary = input.boundaries.lastOrNull { it < tail.end && !streets.beginsSecondary(input.tokens, it) }
        val boundary = cityBoundary
            ?: tail.end.takeIf { end -> end in input.boundaries && (1 until end).none { input.tokens[it] in model.street_abbreviations } }
        val deliveryEnd = boundary ?: tail.end
        val special = if (input.tokens[0][0] in '0'..'9') null
            else parseSpecialDelivery(input.tokens.subList(0, deliveryEnd))
        if (special != null) {
            if (cityBoundary != null && special.consumed != deliveryEnd) invalid("Unexpected words after the box number")
            val city = city(input.tokens, cityBoundary ?: special.consumed, localityEnd)
            validateMilitary(city, state, special.type)
            return USStandardizedAddress(
                "", "", "", "", "", "", "", city, state, postalCode,
                special.type, special.routeNumber, special.boxNumber, special.militaryUnit,
            )
        }

        tail.end = deliveryEnd
        val street = streets.parse(tail, boundary != null)
        val city = city(input.tokens, boundary ?: street.consumed, localityEnd)
        validateMilitary(city, state, USAddressType.STREET)
        return USStandardizedAddress(
            street.number, street.preDirection, street.name, street.suffix, street.postDirection,
            street.unit, street.unitRange, city, state, postalCode,
        )
    }

    private fun postalCode(tokens: TokenCursor): String {
        val last = tokens.last() ?: return ""
        when (last.length) {
            5 -> if (last.isAsciiDigits()) { tokens.end--; return last }
            10 -> if (last[5] == '-' && last.isAsciiDigits(0, 5) && last.isAsciiDigits(6)) { tokens.end--; return last }
            9 -> if (last.isAsciiDigits()) { tokens.end--; return last.take(5) + "-" + last.takeLast(4) }
            4 -> {
                val previous = tokens.last(1)
                if (last.isAsciiDigits() && previous?.length == 5 && previous.isAsciiDigits()) {
                    tokens.end -= 2
                    return "$previous-$last"
                }
            }
        }
        if (last.any { it.isDigit() }) invalid("Invalid ZIP code '$last'; expected 12345 or 12345-6789")
        return ""
    }

    private fun state(tokens: TokenCursor): String {
        val last = tokens.last() ?: return ""
        for (length in model.stateWordCountsByLastToken[last] ?: return "") {
            if (length > tokens.size) continue
            val candidate = if (length == 1) last else tokens.text(tokens.end - length)
            model.states[candidate]?.let { tokens.end -= length; return it }
        }
        return ""
    }

    private fun city(tokens: List<String>, from: Int, until: Int): String {
        if (from < until && (!(from until until).any { index -> tokens[index].any { it.isLetter() } } || (from until until).any { index ->
                val word = tokens[index]
                word.none { it.isLetterOrDigit() } || word.any { !it.isLetterOrDigit() && it != '-' && it != '\'' }
            })) invalid("Invalid city or unparsed address components: ${tokens.text(from, until)}")
        return tokens.text(from, until)
    }

    private fun validateMilitary(city: String, state: String, type: USAddressType) {
        val militaryCity = city in MILITARY_CITIES
        val militaryState = state in MILITARY_STATES
        if (militaryCity != militaryState || (type == USAddressType.MILITARY && !militaryCity)) {
            invalid("Military addresses require APO, FPO, or DPO with AA, AE, or AP")
        }
    }

    private companion object {
        val MILITARY_CITIES = setOf("APO", "FPO", "DPO")
        val MILITARY_STATES = setOf("AA", "AE", "AP")
    }
}

internal fun invalid(message: String): Nothing = throw InvalidAddressException(message)
