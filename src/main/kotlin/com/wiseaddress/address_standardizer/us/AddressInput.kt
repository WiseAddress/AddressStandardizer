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
        val normalized = address.uppercase(Locale.ROOT)
        var start = -1
        var hasPeriod = false
        for (index in normalized.indices) {
            val character = normalized[index]
            when {
                character in 'A'..'Z' || character in '0'..'9' || character in "-/'." ||
                    (character.code >= 128 && character.isLetterOrDigit()) -> {
                    if (start < 0) start = index
                    if (character == '.') hasPeriod = true
                }
                character == ',' || character.isWhitespace() || character == '#' -> {
                    if (start >= 0) {
                        val word = normalized.substring(start, index)
                        tokens.add(if (hasPeriod) normalizePeriods(word) else word)
                        start = -1
                        hasPeriod = false
                    }
                    when (character) {
                        ',', '\n', '\r' -> if (tokens.isNotEmpty() && boundaries.lastOrNull() != tokens.size) boundaries.add(tokens.size)
                        '#' -> tokens.add("#")
                    }
                }
                else -> throw InvalidAddressException("Unsupported character '$character'")
            }
        }
        if (start >= 0) {
            val word = normalized.substring(start)
            tokens.add(if (hasPeriod) normalizePeriods(word) else word)
        }
        if (tokens.isEmpty()) throw InvalidAddressException("Address has no components")
    }

    private companion object {
        val INITIALS = Regex("(?:\\p{L}\\.)+\\p{L}?")

        fun normalizePeriods(word: String): String {
            val dot = word.indexOf('.')
            if (dot > 0 && word.isAsciiDigits(0, dot) && word.isAsciiDigits(dot + 1)) return word
            val trimmed = word.trimEnd('.')
            return when {
                trimmed.isNotEmpty() && trimmed.all(Char::isLetter) -> trimmed
                INITIALS.matches(word) -> word.replace(".", "")
                else -> throw InvalidAddressException("Invalid punctuation in '$word'")
            }
        }
    }
}

internal class TokenCursor(val tokens: List<String>, var start: Int = 0, var end: Int = tokens.size) {
    val size: Int get() = end - start
    fun first(offset: Int = 0): String? = tokens.getOrNull(start + offset)?.takeIf { start + offset < end }
    fun last(offset: Int = 0): String? = tokens.getOrNull(end - 1 - offset)?.takeIf { end - 1 - offset >= start }
    fun text(from: Int = start, until: Int = end): String = tokens.text(from, until)
}

internal fun List<String>.text(from: Int = 0, until: Int = size): String {
    if (from == until) return ""
    if (from + 1 == until) return this[from]
    val result = StringBuilder(this[from])
    for (index in from + 1 until until) result.append(' ').append(this[index])
    return result.toString()
}

internal fun String.isAsciiDigits(from: Int = 0, until: Int = length): Boolean {
    if (from >= until) return false
    for (index in from until until) if (this[index] !in '0'..'9') return false
    return true
}
