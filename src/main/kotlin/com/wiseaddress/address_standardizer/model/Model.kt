package com.wiseaddress.address_standardizer.model

import java.io.Serializable

data class Model(val street_abbreviations: HashMap<String, String>, val secondary_units: HashMap<String, String>, val secondary_range_unneeded: List<String>, val states: HashMap<String, String>, val directions: HashMap<String, String>, val miscellaneous: HashMap<String, String>, val csl: HashMap<String, String>) : Serializable
