package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.AddressStandardizer
import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import com.wiseaddress.address_standardizer.model.Model
import com.wiseaddress.address_standardizer.model.ModelFactory

/** Offline US address parser. Instances and their immutable lookup data can be shared across threads. */
class USStandardizer @JvmOverloads constructor(private val model: Model = ModelFactory.load("us")) : AddressStandardizer {
    private val secondaries = SecondaryParser(model)
    private val streets = StreetParser(model, secondaries)

    override fun standardize(address: String): USStandardizedAddress {
        val input = AddressInput(address)
        val tail = TokenCursor(input.tokens)
        val postalCode = postalCode(tail)
        val state = state(tail, input.boundaries)
        if (state.isEmpty() && postalCode.isEmpty()) invalid("A valid state or ZIP code is required")
        if (tail.size == 0) invalid("Delivery address is missing")
        val layout = DeliveryLayout(input, tail.end, state, secondaries)
        val tokens = layout.tokens
        val cityBoundary = layout.boundaries.lastOrNull {
            it < layout.end && !streets.beginsSecondary(tokens, it, layout.end)
        }?.takeUnless {
            layout.end in layout.boundaries &&
                (streets.beginsDelivery(tokens, it, layout.end) || beginsSpecialDelivery(tokens, it, layout.end))
        }
        val boundary = cityBoundary ?: layout.end.takeIf { end ->
            end in layout.boundaries && (1 until end).none { tokens[it] in model.street_abbreviations }
        }
        val lines = layout.deliveryLines(boundary ?: layout.end)
        var selected: USStandardizedAddress? = null
        for (line in lines) {
            val parsed = parseDelivery(line.tokens, boundary != null || line.above.isNotEmpty() || lines.size > 1, state, postalCode) { consumed ->
                if ((cityBoundary != null || lines.size > 1 || line.above.isNotEmpty()) && consumed != line.tokens.size) {
                    invalid("Unexpected words after the delivery address")
                }
                city(layout, cityBoundary ?: if (line.above.isNotEmpty()) layout.end else consumed, layout.end)
            }
            selected = if (line.above.isEmpty()) parsed else addSecondaryAbove(parsed, line.above)
        }
        val result = selected ?: invalid("Delivery address is missing")
        validateLocality(result.city, state, postalCode, result.addressType)
        if (layout.urbanization.isEmpty()) return result
        if (result.addressType != USAddressType.STREET) invalid("Urbanization requires a street address")
        return result.copy(urbanization = layout.urbanization)
    }

    private inline fun parseDelivery(
        tokens: List<String>, explicitBoundary: Boolean, state: String, postalCode: String,
        city: (Int) -> String,
    ): USStandardizedAddress {
        val special = if (tokens[0][0] in '0'..'9') null else parseSpecialDelivery(tokens, state, explicitBoundary)
        if (special != null) {
            return USStandardizedAddress(
                "", "", "", "", "", "", "", city(special.consumed), state, postalCode,
                special.type, special.routeNumber, special.boxNumber, special.militaryUnit,
                privateMailbox = special.privateMailbox,
            )
        }
        val street = streets.parse(TokenCursor(tokens), explicitBoundary, preserveStreetWords = state == "PR")
        return USStandardizedAddress(
            street.number, street.preDirection, street.name, street.suffix, street.postDirection,
            street.unit, street.unitRange, city(street.consumed), state, postalCode, privateMailbox = street.privateMailbox,
        )
    }

    private fun addSecondaryAbove(address: USStandardizedAddress, tokens: List<String>): USStandardizedAddress {
        val cursor = TokenCursor(tokens)
        val secondary = secondaries.parse(cursor, explicitBoundary = true)
        if (cursor.size != 0) invalid("Unexpected words on secondary line")
        val numberSignMailbox = secondary.privateMailbox.isEmpty() && secondary.unit == "#" &&
            (address.secondaryIdentifier.isNotEmpty() || address.addressType != USAddressType.STREET)
        val mailbox = if (numberSignMailbox) secondary.range else secondary.privateMailbox
        val unit = if (numberSignMailbox) "" else secondary.unit
        if (mailbox.isNotEmpty()) {
            if (address.privateMailbox.isNotEmpty()) invalid("Duplicate private mailbox")
            if (address.addressType == USAddressType.MILITARY || address.addressType == USAddressType.GENERAL_DELIVERY) {
                invalid("Private mailbox requires a physical delivery address")
            }
        }
        if (unit.isNotEmpty() && (address.addressType != USAddressType.STREET || address.secondaryIdentifier.isNotEmpty())) {
            invalid("Secondary line requires a street without another physical unit")
        }
        return address.copy(
            secondaryIdentifier = unit.ifEmpty { address.secondaryIdentifier },
            secondaryRange = if (unit.isEmpty()) address.secondaryRange else secondary.range,
            privateMailbox = mailbox.ifEmpty { address.privateMailbox },
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

    private fun state(tokens: TokenCursor, boundaries: List<Int>): String {
        val last = tokens.last() ?: return ""
        for (length in model.stateWordCountsByLastToken[last] ?: return "") {
            if (length > tokens.size) continue
            if (length > 1 && boundaries.any { it > tokens.end - length && it < tokens.end }) continue
            val candidate = if (length == 1) last else tokens.text(tokens.end - length)
            model.states[candidate]?.let { tokens.end -= length; return it }
        }
        return ""
    }

    private fun city(layout: DeliveryLayout, from: Int, until: Int): String {
        val tokens = layout.tokens
        if (from < until && (beginsSpecialDelivery(tokens, from, until) || streets.beginsDelivery(tokens, from, until))) {
            invalid("Separate each delivery address from the city with a comma or newline")
        }
        if (from < until && streets.beginsSecondary(tokens, from, until) || layout.boundaries.any {
                it > from && it < until && streets.beginsSecondary(tokens, it, until)
            }) invalid("Secondary unit information must precede the city")
        if (from < until && (!(from until until).any { index -> tokens[index].any { it.isLetter() } } || (from until until).any { index ->
                val word = tokens[index]
                word.none { it.isLetterOrDigit() } || word.any { !it.isLetterOrDigit() && it != '-' && it != '\'' }
            })) invalid("Invalid city or unparsed address components: ${tokens.text(from, until)}")
        return tokens.text(from, until)
    }

    private fun validateLocality(city: String, state: String, postalCode: String, type: USAddressType) {
        val militaryCity = city in MILITARY_CITIES
        val militaryState = state in MILITARY_STATES
        if (militaryCity != militaryState || (type == USAddressType.MILITARY && !militaryCity)) {
            invalid("Military addresses require APO, FPO, or DPO with AA, AE, or AP")
        }
        if (militaryCity && postalCode.isEmpty()) invalid("Military addresses require a ZIP code")
        if (type == USAddressType.GENERAL_DELIVERY && postalCode.length == 10 && !postalCode.endsWith("-9999")) {
            invalid("General delivery ZIP+4 must use the 9999 add-on")
        }
    }

    private companion object {
        val MILITARY_CITIES = setOf("APO", "FPO", "DPO")
        val MILITARY_STATES = setOf("AA", "AE", "AP")
    }
}

internal fun invalid(message: String): Nothing = throw InvalidAddressException(message)
