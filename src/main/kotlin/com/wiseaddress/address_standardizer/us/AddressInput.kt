package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import java.util.Locale

/** Boundaries retain information that would be lost by flattening a mailing label. */
internal class AddressInput(address: String) {
    val tokens = ArrayList<String>()
    val boundaries = ArrayList<Int>()

    init {
        if (address.isBlank()) throw InvalidAddressException("Address is empty")
        if (address.length > 4096) throw InvalidAddressException("Address exceeds 4096 characters")
        val word = StringBuilder()
        fun flush() {
            if (word.isEmpty()) return
            val raw = word.toString()
            val token = when {
                '.' !in raw -> raw
                raw.trimEnd('.').let { it.isNotEmpty() && it.all(Char::isLetter) } -> raw.trimEnd('.')
                INITIALS.matches(raw) -> raw.replace(".", "")
                else -> throw InvalidAddressException("Invalid punctuation in '$raw'")
            }
            tokens.add(token)
            word.setLength(0)
        }
        for (character in address.uppercase(Locale.ROOT)) {
            when {
                character == ',' || character == '\n' || character == '\r' -> {
                    flush()
                    if (tokens.isNotEmpty() && boundaries.lastOrNull() != tokens.size) boundaries.add(tokens.size)
                }
                character.isWhitespace() -> flush()
                character == '#' -> { flush(); tokens.add("#") }
                character.isLetterOrDigit() || character in "-/'." -> word.append(character)
                else -> throw InvalidAddressException("Unsupported character '$character'")
            }
        }
        flush()
        if (tokens.isEmpty()) throw InvalidAddressException("Address has no components")
    }

    private companion object {
        val INITIALS = Regex("(?:\\p{L}\\.)+\\p{L}?")
    }
}

internal class TokenCursor(val tokens: List<String>, var start: Int = 0, var end: Int = tokens.size) {
    val size: Int get() = end - start
    fun first(offset: Int = 0): String? = tokens.getOrNull(start + offset)?.takeIf { start + offset < end }
    fun last(offset: Int = 0): String? = tokens.getOrNull(end - 1 - offset)?.takeIf { end - 1 - offset >= start }
    fun text(from: Int = start, until: Int = end): String = tokens.subList(from, until).joinToString(" ")
}

internal fun String.isAsciiDigits(from: Int = 0, until: Int = length): Boolean {
    if (from >= until) return false
    for (index in from until until) if (this[index] !in '0'..'9') return false
    return true
}
