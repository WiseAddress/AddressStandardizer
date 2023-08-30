package com.wiseaddress.address_standardizer.us

import com.wiseaddress.address_standardizer.AddressStandardizer
import com.wiseaddress.address_standardizer.exception.InvalidAddressException
import com.wiseaddress.address_standardizer.model.Model
import com.wiseaddress.address_standardizer.model.ModelFactory

class USStandardizer : AddressStandardizer {
    private var model: Model = ModelFactory.load("us");

    private fun getPrimaryRange(splitAddress: MutableList<String>) : String {
        // TODO fix ranges (alphanumeric and fractional)
        return splitAddress.removeAt(0)
    }

    private fun getPostalCode(splitAddress: MutableList<String>): String {
        val last = splitAddress.removeAt(splitAddress.size - 1)
        when (last.length) {
            10, 5 -> {
                return last
            }
            9 -> {
                return "${last.substring(0,5)}-${last.substring(5,9)}"
            }
            4 -> {
                val secondLast = splitAddress.removeAt(splitAddress.size - 1)
                if (secondLast.length == 5) {
                    return "$secondLast-$last"
                }
            }
        }
        return ""
    }

    private fun getState(splitAddress: MutableList<String>): String {
        val last = splitAddress.removeAt(splitAddress.size - 1)
        if (model.states.contains(last)) {
            return model.states[last] as String
        }

        val secondLast = splitAddress.removeAt(splitAddress.size - 1)
        val guessedState = "$secondLast $last"
        if (model.states.contains(guessedState)) {
            return model.states[guessedState] as String
        }

        splitAddress.addAll(guessedState.split(" "))
        return ""
    }

    private fun getDirection(splitAddress: MutableList<String>): String {
        val first = splitAddress.removeAt(0)
        if (model.directions.contains(first)) {
            val second = splitAddress.removeAt(0)
            if (model.directions.contains("$first$second")) {
                return model.directions["$first$second"] as String
            }
            splitAddress.add(0, second)
            return model.directions[first] as String;
        }

        splitAddress.add(0, first)
        return ""
    }

    private fun handleSpecialStreets(splitAddress: MutableList<String>): Pair<String, String>? {
        // TODO actually handle the special cases but we are not doing that right now because its annoying
        return null
    }

    private fun getStreet(splitAddress: MutableList<String>): Pair<String, String> {

        val special = handleSpecialStreets(splitAddress)
        if (special != null) {
            return special
        }

        var found = false
        var streetName = ""
        var suffix = ""

        try {
            while (!found) {

                val section = splitAddress.removeAt(0)
                if (model.street_abbreviations.contains(section)) {
                    val nextSection = splitAddress.removeAt(0)
                    if (model.street_abbreviations.contains(nextSection)) {
                        streetName = "$streetName $section"
                        suffix = model.street_abbreviations[nextSection] as String
                    }
                    else {
                        suffix = model.street_abbreviations[section] as String
                        splitAddress.add(0, nextSection)
                    }
                    found = true
                } else {
                    streetName = "$streetName $section".trim()
                }
            }
        } catch (e: Exception) {
            throw InvalidAddressException("Could not find a valid suffix based on the USPS specifications! See https://pe.usps.com/text/pub28/28apc_002.htm for more info!")
        }

        return Pair(streetName, suffix)
    }

    private fun getCity(splitAddress: MutableList<String>): String {
        return splitAddress.joinToString(" ")
    }

    override fun standardize(adr : String): USStandardizedAddress {
        val newAddress = adr.uppercase()
        val splitAddress = newAddress.split(' ').toMutableList()

        val primaryRange = getPrimaryRange(splitAddress)
        val postalCode = getPostalCode(splitAddress)
        val state = getState(splitAddress)
        if (postalCode == "" && state == "") {
            throw InvalidAddressException("Neither a valid state nor a valid postal code found in the address!")
        }

        val preDirection = getDirection(splitAddress)
        val (streetName, suffix) = getStreet(splitAddress)
        val postDirection = if (preDirection == "") getDirection(splitAddress) else ""

        // TODO secondary stuff
        val secondaryIdentifier = ""
        val secondaryRange = ""
        val city = getCity(splitAddress)
        return USStandardizedAddress(primaryRange, preDirection, streetName, suffix, postDirection, secondaryIdentifier, secondaryRange, city, state, postalCode)
    }
}