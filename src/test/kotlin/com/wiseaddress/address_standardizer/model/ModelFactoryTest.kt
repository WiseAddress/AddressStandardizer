package com.wiseaddress.address_standardizer.model

import com.wiseaddress.address_standardizer.exception.InvalidModelException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ModelFactoryTest {
    @Test
    fun `bundled model is loaded from the classpath and shared`() {
        val model = ModelFactory.load()

        assertSame(model, ModelFactory.load("US"))
        assertEquals("ST", model.street_abbreviations["STREET"])
        assertEquals("DC", model.states["DISTRICT OF COLUMBIA"])
        assertEquals("MDW", model.street_abbreviations["MDW"])
        assertEquals("MDW", model.street_abbreviations["MEADOW"])
        assertEquals("MDWS", model.street_abbreviations["MEADOWS"])
        assertEquals("FALL", model.street_abbreviations["FALL"])
        assertEquals("PARK", model.street_abbreviations["PRK"])
        assertEquals("KEY", model.secondary_units["KEY"])
        val territories = mapOf(
            "AS" to "AMERICAN SAMOA", "FM" to "FEDERATED STATES OF MICRONESIA", "GU" to "GUAM",
            "MH" to "MARSHALL ISLANDS", "MP" to "NORTHERN MARIANA ISLANDS", "PW" to "PALAU",
            "PR" to "PUERTO RICO", "VI" to "VIRGIN ISLANDS",
        )
        territories.forEach { (code, name) -> assertEquals(code, model.states[name]) }
        for (lookup in lookups(model)) {
            lookup.forEach { (_, canonical) -> assertEquals(canonical, lookup[canonical]) }
        }
    }

    @Test
    fun `unsupported model names do not become file paths`() {
        for (name in listOf("", "ca", "../../us")) {
            assertFailsWith<InvalidModelException> { ModelFactory.load(name) }
        }
    }

    @Test
    fun `explicit file loading preserves IO failures`(@TempDir directory: Path) {
        val file = directory.resolve("custom.json").toFile()
        file.writeText(validJson)
        assertEquals("ST", ModelFactory.load(file).street_abbreviations["STREET"])
        val error = assertFailsWith<InvalidModelException> { ModelFactory.load(directory.resolve("missing.json").toFile()) }
        assertTrue(error.message.orEmpty().contains("missing.json"))
        assertNotNull(error.cause)
    }

    @Test
    fun `malformed JSON has a useful cause and trailing input is rejected`() {
        val error = assertFailsWith<InvalidModelException> { ModelFactory.parse("{") }
        assertNotNull(error.cause)
        for (json in listOf("[]", validJson + "{}", "{'secondary':{}}", "{\"states\":1,\"states\":2}")) {
            assertFailsWith<InvalidModelException> { ModelFactory.parse(json) }
        }
    }

    @Test
    fun `all lookup sections are required and correctly typed`() {
        for (key in listOf("street_abbreviations", "secondary", "states", "directions", "miscellaneous", "county_state_localhwy")) {
            val missing = JSONObject(validJson).apply { remove(key) }
            assertInvalid(missing, key)
            for (wrong in listOf(JSONObject.NULL, "text", JSONArray(), 12)) {
                assertInvalid(JSONObject(validJson).put(key, wrong), key)
            }
        }
        for (key in listOf("units", "range_unneeded")) {
            val missing = JSONObject(validJson)
            missing.getJSONObject("secondary").remove(key)
            assertInvalid(missing, "secondary.$key")
        }
    }

    @Test
    fun `empty lookup tables fail visibly`() {
        for (key in listOf("street_abbreviations", "states", "directions", "miscellaneous", "county_state_localhwy")) {
            assertInvalid(JSONObject(validJson).put(key, JSONObject()), key)
        }
    }

    @Test
    fun `alias values must have the expected type and nonblank text`() {
        for (wrong in listOf(7, JSONObject.NULL, "", "  ")) {
            val data = JSONObject(validJson)
            data.getJSONObject("states").put("IL", wrong)
            assertInvalid(data, "states.IL")
        }
        for (wrong in listOf(JSONArray().put(7), JSONArray().put(JSONObject.NULL), JSONArray().put(" "), "STREET")) {
            val data = JSONObject(validJson)
            data.getJSONObject("street_abbreviations").put("ST", wrong)
            assertInvalid(data, "street_abbreviations.ST")
        }
    }

    @Test
    fun `conflicting aliases cannot replace canonical identifiers`() {
        for (table in listOf(
            """{"MDW":["MEADOW"],"MDWS":["MDW","MEADOWS"]}""",
            """{"ST":["SHARED"],"RD":["SHARED"]}""",
            """{"ST":["STREET"],"st":["STRT"]}""",
            """{"ST":["STREET","street"]}""",
        )) {
            assertInvalid(JSONObject(validJson).put("street_abbreviations", JSONObject(table)), "street_abbreviations")
        }
    }

    @Test
    fun `optional ranges must name known canonical units without duplicates`() {
        for (codes in listOf(listOf("NOPE"), listOf("BASEMENT"), listOf("BSMT", "bsmt"))) {
            val data = JSONObject(validJson)
            data.getJSONObject("secondary").put("range_unneeded", JSONArray(codes))
            assertInvalid(data, "secondary.range_unneeded")
        }
    }

    @Test
    fun `model normalization is independent of the default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val model = ModelFactory.parse(validJson.lowercase(Locale.ROOT))
            assertEquals("IL", model.states["ILLINOIS"])
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `constructor copies caller owned collections and returned data is immutable`() {
        val bundled = ModelFactory.load()
        val streets = bundled.street_abbreviations.toMutableMap()
        val ranges = bundled.secondary_range_unneeded.toMutableList()
        val model = copyModel(streets, ranges)
        streets.clear()
        ranges.clear()

        assertEquals("ST", model.street_abbreviations["STREET"])
        assertTrue(model.secondary_range_unneeded.isNotEmpty())
        for (lookup in lookups(model)) {
            assertFailsWith<UnsupportedOperationException> { (lookup as MutableMap<String, String>).clear() }
        }
        assertFailsWith<UnsupportedOperationException> { (model.secondary_range_unneeded as MutableList<String>).clear() }
    }

    @Test
    fun `direct model construction cannot bypass lookup validation`() {
        for (streets in listOf(emptyMap(), mapOf("STREET" to "ST"), mapOf("ST" to "ST", "st" to "ST"))) {
            assertFailsWith<InvalidModelException> { copyModel(streets) }
        }
        assertFailsWith<InvalidModelException> { copyModel(ranges = listOf("UNKNOWN")) }
    }

    @Test
    fun `shared match indexes preserve overlapping alias lengths and cannot be mutated`() {
        val model = ModelFactory.load()
        assertEquals(listOf(2, 1), model.stateWordCountsByLastToken["VIRGINIA"])
        assertEquals(listOf(3), model.stateWordCountsByLastToken["COLUMBIA"])
        assertEquals(listOf(3, 2, 1), model.highwayWordCountsByFirstToken["KY"])
        for (index in listOf(model.stateWordCountsByLastToken, model.highwayWordCountsByFirstToken)) {
            assertFailsWith<UnsupportedOperationException> { (index as MutableMap<String, List<Int>>).clear() }
            assertFailsWith<UnsupportedOperationException> { (index.values.first() as MutableList<Int>).clear() }
        }
    }

    private fun copyModel(
        streets: Map<String, String> = ModelFactory.load().street_abbreviations,
        ranges: List<String> = ModelFactory.load().secondary_range_unneeded,
    ): Model = ModelFactory.load().let { source ->
        Model(streets, source.secondary_units, ranges, source.states, source.directions, source.miscellaneous, source.csl)
    }

    private fun lookups(model: Model): List<Map<String, String>> = listOf(
        model.street_abbreviations, model.secondary_units, model.states, model.directions, model.miscellaneous, model.csl,
    )

    private fun assertInvalid(data: JSONObject, path: String) {
        val error = assertFailsWith<InvalidModelException> { ModelFactory.parse(data.toString()) }
        assertTrue(error.message.orEmpty().contains(path), "Expected $path in ${error.message}")
    }

    private val validJson = """
        {
          "street_abbreviations": {"ST": ["STREET"]},
          "secondary": {"units": {"BSMT": "BASEMENT"}, "range_unneeded": ["BSMT"]},
          "states": {"IL": "Illinois"},
          "directions": {"N": "North"},
          "miscellaneous": {"HC": ["HIGHWAY CONTRACT ROUTE"]},
          "county_state_localhwy": {"COUNTY ROAD": ["CR"]}
        }
    """.trimIndent()
}
