package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.AddressStandardizer
import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import com.wiseaddress.address_standardizer.model.Model
import com.wiseaddress.address_standardizer.model.ModelFactory

class USStandardizer : AddressStandardizer {
    private var model: Model = ModelFactory.load("us");

    override fun standardize(address: String): USStandardizedAddress {
        val splitAddress = address.uppercase().split(' ').toMutableList()

        val primaryRange = getPrimaryRange(splitAddress)
            ?: throw InvalidAddressException("No valid address range found! It should be a number, that may have a letter attached.")

        val postalCode = getPostalCode(splitAddress)
        val state = getState(splitAddress)
        if (postalCode == "" && state == "")
            throw InvalidAddressException("Neither a valid state nor a valid postal code found in the address!")

        val preDirection = getDirection(splitAddress)
        val (streetName, suffix) = getStreet(splitAddress)
            ?: throw InvalidAddressException("Could not find a valid suffix based on the USPS specifications! See https://pe.usps.com/text/pub28/28apc_002.htm for more info!")

        val postDirection = if (preDirection == "") getDirection(splitAddress) else ""
        val (secondaryIdentifier, secondaryRange) = getSecondaryAddress(splitAddress)
            ?: throw InvalidAddressException("TODO message")
        val city = getCity(splitAddress)

        return USStandardizedAddress(primaryRange, preDirection, streetName, suffix, postDirection, secondaryIdentifier, secondaryRange, city, state, postalCode)
    }

    private fun getPrimaryRange(splitAddress: MutableList<String>) : String? {
        // checks for permitted characters in first word
        if (splitAddress[0].all { it.isLetterOrDigit() || it == '.' || it == '-' }) {
            // checks for fraction in second word
            if (splitAddress[1].all { it.isDigit() || it == '/' }) {
                return "${splitAddress.removeFirst()} ${splitAddress.removeFirst()}"
            }
            return splitAddress.removeFirst()
        }
        return null
    }

    private fun getPostalCode(splitAddress: MutableList<String>): String {
        val last = splitAddress.removeLast()
        when (last.length) {
            // zip or zip+4 formatted perfectly
            10, 5 -> {
                return last
            }
            // zip+4 with no separator
            9 -> {
                return "${last.substring(0,5)}-${last.substring(5,9)}"
            }
            // zip+4 with a space (or invalid)
            4 -> {
                val secondLast = splitAddress.removeLast()
                if (secondLast.length == 5) {
                    return "$secondLast-$last"
                }
                // put back word if not valid
                splitAddress.add(secondLast)
            }
        }
        // put back word if not valid
        splitAddress.add(last)
        return ""
    }

    private fun getState(splitAddress: MutableList<String>): String {
        // longest US state name is currently 2 words
        val last = splitAddress.removeLast()
        val secondLast = splitAddress.removeLast()
        // checking the last two words first in case of West Virginia
        // (Virginia is also a valid state)
        val guessedState = "$secondLast $last"

        if (model.states.contains(guessedState)) {
            return model.states[guessedState] as String
        }
        // put back the words if not found
        splitAddress.add(secondLast)

        if (model.states.contains(last)) {
            return model.states[last] as String
        }
        // put back the words if not found
        splitAddress.add(last)
        return ""
    }

    private fun getDirection(splitAddress: MutableList<String>): String {
        val first = splitAddress.removeFirst()
        if (model.directions.contains(first)) {
            val second = splitAddress.removeFirst()
            if (model.directions.contains("$first$second")) {
                return model.directions["$first$second"] as String
            }
            // put back words if not found
            splitAddress.add(0, second)
            return model.directions[first] as String;
        }

        // put back words if not found
        splitAddress.add(0, first)
        return ""
    }

    private fun getStreet(splitAddress: MutableList<String>): Pair<String, String>? {
        // delegate to first function for special cases, second function for standard cases
        return handleSpecialStreets(splitAddress) ?: handleStandardStreets(splitAddress)
    }

    private fun handleSpecialStreets(splitAddress: MutableList<String>): Pair<String, String>? {
        // TODO actually handle the special cases -- currently a placeholder
        return null
    }

    // TODO remove old code -- I modified it slightly to make it trim once at the end before returning
    private fun oldHandleStandardStreets(splitAddress: MutableList<String>): Pair<String, String> {
        var found = false
        var streetName = ""
        var suffix = ""
        try {
            while (!found) {
                val section = splitAddress.removeFirst()
                if (model.street_abbreviations.contains(section)) {
                    val nextSection = splitAddress.removeFirst()
                    if (model.street_abbreviations.contains(nextSection)) {
                        streetName = "$streetName $section"
                        suffix = model.street_abbreviations[nextSection] as String
                    } else {
                        suffix = model.street_abbreviations[section] as String
                        splitAddress.add(0, nextSection)
                    }
                    found = true
                } else {
                    streetName = "$streetName $section"
                }
            }
        } catch (e: Exception) {
            throw InvalidAddressException("Could not find a valid suffix based on the USPS specifications! See https://pe.usps.com/text/pub28/28apc_002.htm for more info!")
        }
        return Pair(streetName.trim(), suffix.trim())
    }

    private fun handleStandardStreets(splitAddress: MutableList<String>): Pair<String, String>? {
        // TODO rewrite to deal with cases where there are three suffixes in a row?
            // go from left to right, check for suffixes
            // when one is found, check if there's another one after it
            // if there is, move further to the right and check again
        // TODO also deal with two word suffixes (Ranch Road, etc.)
            // maybe take care of these in special cases

        return null
    }

    private fun getSecondaryAddress(splitAddress: MutableList<String>): Pair<String, String>? {
        val section = splitAddress.removeFirst()

        // dealing with stuff like "#357" when it should be "# 357"
        if (section.length > 1 && section.all { it == '#' || it.isDigit() }) {
            return Pair("#", section.substring(1))
        }

        val identifier: String
        val range: String?

        // normal procedure
        if (section == "#") {
            identifier = "#"
        } else if (model.secondary_units.contains(section)) {
            identifier = model.secondary_units[section] ?: "" // as String
        } else {
            identifier = ""
            // put back words if not found
            splitAddress.add(0, section)
        }

        // TODO there has to be a way to bundle this information in the original json file instead of hard-coding it in here
        range = when (identifier) {
            // no range if no identifier
            "" -> ""
            // must have a range, will evaluate as null if not found
            "#", "APT", "BLDG", "DEPT", "FL", "HNGR", "KEY", "LOT", "PIER", "RM", "SLIP", "SPC", "STOP", "STE", "TRLR", "UNIT"
                -> getSecondaryRange(splitAddress)
            // may not have a range, will evaluate as empty string if not found
            "BSMT", "FRNT", "LBBY", "LOWR", "OFC", "PH", "REAR", "SIDE", "UPPR"
                -> getSecondaryRange(splitAddress) ?: ""
            // this should never happen
            else -> null
        } ?: return null // return null only if a secondary range is required but none is found

        return Pair(identifier, range)
    }

    // TODO this may need updating later, but for now this should cover almost everything
    private fun getSecondaryRange(splitAddress: MutableList<String>): String? {
        // checks for permitted characters in first word
        val first = splitAddress.removeFirst()
        if ( (first.all { it.isDigit() }) // 201
            || (first.length == 1 && first[0].isLetter()) // A
            || (first.substring(0, first.length-1).all { it.isDigit() || it == '-' } ) // 201C or 201-C
            || (first.substring(1, first.length).all { it.isDigit() || it == '-' } ) // A200 or A-200
            ) {
            return first
        }
        // put back words if not found
        splitAddress.add(0, first)
        return null
    }

    private fun getCity(splitAddress: MutableList<String>): String {
        // all remaining words by this point should be part of the city name
        return splitAddress.joinToString(" ")
    }
}