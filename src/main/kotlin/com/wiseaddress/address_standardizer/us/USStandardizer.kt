package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.AddressStandardizer
import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import com.wiseaddress.address_standardizer.exception.InvalidModelException
import com.wiseaddress.address_standardizer.model.Model
import com.wiseaddress.address_standardizer.model.ModelFactory

class USStandardizer : AddressStandardizer {
    private var model: Model

    init {
        try {
            model = ModelFactory.load("us")
        } catch (e : InvalidModelException) { // if for some reason the model is corrupted, rethrow and tell user to recreate the model.
            throw InvalidModelException("Could not read the model! " +
                    "Please recreate the model by using the static ModelFactory.create() function, " +
                    "or download the US model and put it under <current working directory>/models/us.model. Original stack trace below:\n${e.stackTraceToString()}")
        }
    }

    // Basically just to output more debug information on an error
    private var currentSecondaryIdentifier = ""

    override fun standardize(address: String): USStandardizedAddress {
        val splitAddress = address.uppercase().split(' ').toMutableList()

        val primaryRange = getPrimaryRange(splitAddress)
            ?: throw InvalidAddressException("No valid address range found! It should be a number, that may have a letter attached. Primary range checked: ${splitAddress[0]}")

        val postalCode = getPostalCode(splitAddress)
        val state = getState(splitAddress)
        if (postalCode == "" && state == "")
            throw InvalidAddressException("Neither a valid state nor a valid postal code found in the address!")

        val preDirection = getDirection(splitAddress)
        val (streetName, suffix) = getStreet(splitAddress)
            ?: throw InvalidAddressException("Could not find a valid suffix based on the USPS specifications!\nSee https://pe.usps.com/text/pub28/28apc_002.htm for more info!")

        val postDirection = if (preDirection == "") getDirection(splitAddress) else ""
        val (secondaryIdentifier, secondaryRange) = getSecondaryAddress(splitAddress)
            ?: throw InvalidAddressException("Could not find a valid secondary range that is required by the current secondary identifier \"$currentSecondaryIdentifier\"." +
                    "\nOnly some identifiers do not require a valid secondary range to follow.\nSee https://pe.usps.com/text/pub28/28apc_003.htm for more info.")
        val city = getCity(splitAddress)

        return USStandardizedAddress(primaryRange, preDirection, streetName, suffix, postDirection, secondaryIdentifier, secondaryRange, city, state, postalCode)
    }

    private fun primaryRangePredicate(it: Char) : Boolean {
        return it.isLetterOrDigit() || it == '.' || it == '-' || it == '/'
    }

    private fun getPrimaryRange(splitAddress: MutableList<String>) : String? {
        // checks for permitted characters in first word
        if (splitAddress[0].all { primaryRangePredicate(it) }) {
            // checks for fraction in second word and make sure that first word doesn't have a fraction
            if (!splitAddress[0].contains("/") && splitAddress[1].all { it.isDigit() || it == '/' }) {
                // handling cases like 1 / 3
                if (splitAddress[1].length == 1) {
                    if (splitAddress[2].all { primaryRangePredicate(it) }) {
                        return "${splitAddress.removeFirst()}${splitAddress.removeFirst()}${splitAddress.removeFirst()}"
                    }
                    return null
                }
                // the normal cases 1 3/4
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
            return model.directions[first] as String
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

    private fun handleStandardStreets(splitAddress: MutableList<String>): Pair<String, String>? {
        var streetName = ""
        var suffix: String
        var current: String
        var next: String
        while (splitAddress.size > 0) {
            current = splitAddress.removeFirst()
            if (model.street_abbreviations.contains(current)) {
                next = splitAddress.removeFirst()
                if (model.street_abbreviations.contains(next)) {
                    streetName = "$streetName $current"
                    suffix = model.street_abbreviations[next] as String
                } else {
                    suffix = model.street_abbreviations[current] as String
                    splitAddress.add(0, next)
                }
                return Pair(streetName.trim(), suffix)
            } else {
                streetName = "$streetName $current"
            }
        }
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

        currentSecondaryIdentifier = identifier

        // errors if a range is needed but none is found, otherwise gets a range if needed
        range = if (identifier == "") "" else getSecondaryRange(splitAddress)
            ?: if (model.secondary_range_unneeded.contains(identifier)) "" else return null

        return Pair(identifier, range)
    }

    private fun getSecondaryRange(splitAddress: MutableList<String>): String? {
        // checks for permitted characters in first word
        val first = splitAddress.removeFirst()
        if ( (first.all { it.isDigit() }) // 201
             || (first.length == 1 && first[0].isLetter()) // A
             || (first.length != 1 // Cases where the range is submitted as #, ?, etc. are accounted for
                    && ((first.substring(0, first.length-1).all { it.isDigit() || it == '-' } ) // 201C or 201-C
                    || (first.substring(1, first.length).all { it.isDigit() || it == '-' } ) // A200 or A-200
            ))) {

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