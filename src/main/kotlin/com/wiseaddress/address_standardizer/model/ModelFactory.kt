package com.wiseaddress.address_standardizer.model

import com.wiseaddress.address_standardizer.exception.InvalidModelException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONParserConfiguration
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.util.Locale

class ModelFactory private constructor() {
    companion object {
        private const val US_RESOURCE = "/com/wiseaddress/address_standardizer/usdata.json"
        private val usModel: Model by lazy {
            val stream = ModelFactory::class.java.getResourceAsStream(US_RESOURCE)
                ?: throw InvalidModelException("Bundled model resource is missing: $US_RESOURCE")
            val json = try {
                stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (error: IOException) {
                throw InvalidModelException("Cannot read bundled model: $US_RESOURCE", error)
            }
            parse(json)
        }

        /** Returns the shared US model, loaded once from the application classpath. */
        @JvmStatic
        @JvmOverloads
        fun load(country: String = "us"): Model {
            if (country.trim().lowercase(Locale.ROOT) != "us") {
                throw InvalidModelException("Unsupported country model: '$country'; available model: us")
            }
            return usModel
        }

        /** Loads an explicit UTF-8 JSON file without changing the bundled model. */
        @JvmStatic
        fun load(file: File): Model {
            val json = try {
                file.readText(Charsets.UTF_8)
            } catch (error: IOException) {
                throw InvalidModelException("Cannot read model file: $file", error)
            }
            return parse(json)
        }

        /** Decodes the model schema and rejects malformed or ambiguous lookup data. */
        @JvmStatic
        fun parse(json: String): Model {
            val data = try {
                val tokens = JSONTokener(json, JSONParserConfiguration().withStrictMode())
                val root = JSONObject(tokens)
                if (tokens.nextClean() != '\u0000') {
                    throw InvalidModelException("Invalid model JSON: unexpected content after the root object")
                }
                root
            } catch (error: JSONException) {
                throw InvalidModelException("Invalid model JSON: ${error.message}", error)
            }
            val secondary = objectSection(data, "secondary")
            return Model(
                street_abbreviations = arrayAliases(data, "street_abbreviations"),
                secondary_units = stringAliases(secondary, "units", "secondary.units"),
                secondary_range_unneeded = stringArray(secondary.opt("range_unneeded"), "secondary.range_unneeded"),
                states = stringAliases(data, "states"),
                directions = stringAliases(data, "directions"),
                miscellaneous = arrayAliases(data, "miscellaneous"),
                csl = arrayAliases(data, "county_state_localhwy"),
            )
        }

        private fun arrayAliases(data: JSONObject, key: String): Map<String, String> =
            expandAliases(objectSection(data, key), key, ::stringArray)

        private fun stringAliases(data: JSONObject, key: String, path: String = key): Map<String, String> =
            expandAliases(objectSection(data, key, path), path) { value, location ->
                listOf(modelString(value, location))
            }

        private fun expandAliases(
            table: JSONObject,
            path: String,
            readAliases: (Any?, String) -> List<String>,
        ): Map<String, String> {
            val lookup = LinkedHashMap<String, String>()
            val keys = table.keySet().sorted()
            for (key in keys) {
                val canonical = normalizeModelToken(key, path)
                if (lookup.putIfAbsent(canonical, canonical) != null) {
                    throw InvalidModelException("$path contains duplicate canonical identifier '$canonical'")
                }
            }
            for (key in keys) {
                val canonical = normalizeModelToken(key, path)
                for (alias in readAliases(table.opt(key), "$path.$key")) {
                    val previous = lookup.putIfAbsent(alias, canonical)
                    if (previous != null && previous != canonical) {
                        throw InvalidModelException("$path: alias '$alias' conflicts between '$previous' and '$canonical'")
                    }
                }
            }
            return lookup
        }

        private fun objectSection(data: JSONObject, key: String, path: String = key): JSONObject =
            data.opt(key) as? JSONObject
                ?: throw InvalidModelException("$path must be a JSON object")

        private fun stringArray(value: Any?, path: String): List<String> {
            val array = value as? JSONArray ?: throw InvalidModelException("$path must be an array of strings")
            val strings = (0 until array.length()).map { index -> modelString(array.opt(index), "$path[$index]") }
            if (strings.size != strings.toSet().size) {
                throw InvalidModelException("$path contains duplicate aliases")
            }
            return strings
        }

        private fun modelString(value: Any?, path: String): String {
            if (value !is String) throw InvalidModelException("$path must be a string")
            return normalizeModelToken(value, path)
        }
    }
}
