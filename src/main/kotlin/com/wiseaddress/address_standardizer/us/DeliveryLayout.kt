package com.wiseaddress.address_standardizer.us

/** A secondary line above a delivery retains its meaning until that delivery has been parsed. */
internal data class DeliveryLine(val tokens: List<String>, val above: List<String> = emptyList())

/** Owns label structure, including urbanizations, separate secondary lines, and dual addresses. */
internal class DeliveryLayout(input: AddressInput, localityEnd: Int, state: String, private val secondaries: SecondaryParser) {
    var tokens: List<String> = input.tokens
        private set
    var boundaries: List<Int> = input.boundaries
        private set
    var end: Int = localityEnd
        private set
    var urbanization: String = ""
        private set

    init {
        if (tokens.first() in URBANIZATION_MARKERS) {
            if (state != "PR") invalid("Urbanization requires Puerto Rico as the state")
            val lineEnd = boundaries.firstOrNull { it < end }
                ?: invalid("Separate the urbanization and street with a comma or newline")
            if (lineEnd < 2 || (1 until lineEnd).none { tokens[it].any(Char::isLetter) }) {
                invalid("Urbanization name is missing")
            }
            urbanization = tokens.text(1, lineEnd)
            tokens = tokens.subList(lineEnd, end)
            boundaries = boundaries.filter { it > lineEnd && it <= end }.map { it - lineEnd }
            end -= lineEnd
        }
    }

    fun deliveryLines(deliveryEnd: Int): List<DeliveryLine> {
        if (boundaries.none { it < deliveryEnd }) return listOf(DeliveryLine(tokens.subList(0, deliveryEnd)))
        val ends = boundaries.filter { it < deliveryEnd } + deliveryEnd
        val lines = ends.zip(listOf(0) + ends).map { (until, from) -> tokens.subList(from, until) }
        val result = ArrayList<DeliveryLine>(2)
        var above = emptyList<String>()
        for ((index, line) in lines.withIndex()) {
            if (secondaries.isDesignator(line.first()) && !beginsSpecialDelivery(line, 0, line.size)) {
                if (above.isNotEmpty()) invalid("Multiple secondary lines are not supported")
                if (index + 1 < lines.size) {
                    above = line
                } else {
                    if (result.isEmpty()) invalid("Secondary line requires a delivery address")
                    val previous = result.removeAt(result.lastIndex)
                    result.add(previous.copy(tokens = previous.tokens + line))
                }
            } else {
                result.add(DeliveryLine(line, above))
                above = emptyList()
                if (result.size > 2) invalid("At most two delivery addresses are supported")
            }
        }
        return result
    }

    private companion object {
        val URBANIZATION_MARKERS = setOf("URB", "URBANIZATION", "URBANIZACION", "URBANIZACIÓN")
    }
}
