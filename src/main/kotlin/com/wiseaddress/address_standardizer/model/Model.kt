package com.wiseaddress.address_standardizer.model

import com.wiseaddress.address_standardizer.exception.InvalidModelException
import java.util.Collections
import java.util.Locale

/** Immutable, validated alias-to-canonical lookup tables, safe to share between standardizers. */
class Model @JvmOverloads constructor(
    street_abbreviations: Map<String, String>,
    secondary_units: Map<String, String>,
    secondary_range_unneeded: List<String>,
    states: Map<String, String>,
    directions: Map<String, String>,
    miscellaneous: Map<String, String>,
    csl: Map<String, String>,
    street_suffix_names: Map<String, String> = emptyMap(),
) {
    val street_abbreviations: Map<String, String> = immutableLookup(street_abbreviations, "street_abbreviations")
    val secondary_units: Map<String, String> = immutableLookup(secondary_units, "secondary.units")
    val states: Map<String, String> = immutableLookup(states, "states")
    val directions: Map<String, String> = immutableLookup(directions, "directions")
    val miscellaneous: Map<String, String> = immutableLookup(miscellaneous, "miscellaneous")
    val csl: Map<String, String> = immutableLookup(csl, "county_state_localhwy")
    internal val streetFullNames: Map<String, String> = suffixNames(street_suffix_names)
    internal val stateWordCountsByLastToken: Map<String, List<Int>> =
        indexWordCounts(this.states.keys) { it.substringAfterLast(' ') }
    internal val highwayWordCountsByFirstToken: Map<String, List<Int>> =
        indexWordCounts(this.csl.keys) { it.substringBefore(' ') }
    val secondary_range_unneeded: List<String> = Collections.unmodifiableList(
        secondary_range_unneeded.map { normalizeModelToken(it, "secondary.range_unneeded") }.also { codes ->
            if (codes.size != codes.toSet().size) {
                throw InvalidModelException("secondary.range_unneeded contains duplicate identifiers")
            }
            for (code in codes) {
                if (this.secondary_units[code] != code) {
                    throw InvalidModelException("secondary.range_unneeded: '$code' is not a canonical secondary unit")
                }
            }
        },
    )

    private fun immutableLookup(source: Map<String, String>, section: String): Map<String, String> {
        if (source.isEmpty()) throw InvalidModelException("$section must not be empty")
        val copy = LinkedHashMap<String, String>(source.size)
        for ((key, value) in source) {
            val alias = normalizeModelToken(key, section)
            val canonical = normalizeModelToken(value, "$section.$key")
            if (copy.putIfAbsent(alias, canonical) != null) {
                throw InvalidModelException("$section contains duplicate alias '$alias'")
            }
        }
        for (canonical in copy.values) {
            if (copy[canonical] != canonical) {
                throw InvalidModelException("$section: canonical identifier '$canonical' must map to itself")
            }
        }
        return Collections.unmodifiableMap(copy)
    }

    private fun suffixNames(source: Map<String, String>): Map<String, String> {
        val names = LinkedHashMap<String, String>()
        for ((key, value) in source) {
            val canonical = normalizeModelToken(key, "street_suffix_names")
            val full = normalizeModelToken(value, "street_suffix_names.$key")
            if (canonical !in street_abbreviations || street_abbreviations[full] != street_abbreviations[canonical]) {
                throw InvalidModelException("street_suffix_names.$key must identify a known suffix and its full name")
            }
            if (names.putIfAbsent(canonical, full) != null) {
                throw InvalidModelException("street_suffix_names contains duplicate canonical identifier '$canonical'")
            }
        }
        return Collections.unmodifiableMap(street_abbreviations.mapNotNull { (alias, canonical) ->
            (names[alias] ?: names[canonical])?.let { alias to it }
        }.toMap())
    }

    private fun indexWordCounts(aliases: Set<String>, boundaryToken: (String) -> String): Map<String, List<Int>> {
        val counts = LinkedHashMap<String, MutableSet<Int>>()
        for (alias in aliases) {
            counts.getOrPut(boundaryToken(alias)) { HashSet() }.add(alias.count { it == ' ' } + 1)
        }
        return Collections.unmodifiableMap(counts.mapValues { (_, lengths) ->
            Collections.unmodifiableList(lengths.sortedDescending())
        })
    }
}

private val modelWhitespace = Regex("[\\s\\p{Z}]+")

internal fun normalizeModelToken(value: String, path: String): String {
    val normalized = value.trim().replace(modelWhitespace, " ").uppercase(Locale.ROOT)
    if (normalized.isBlank()) throw InvalidModelException("$path must contain a nonblank string")
    return normalized
}
