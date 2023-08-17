package com.wiseaddress.address_standardizer.model


import com.wiseaddress.address_standardizer.exception.InvalidModelException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.*
import java.lang.Exception


class ModelFactory {
    companion object {
        /**
         * Creates a model that can be used by the standardizer
         * @param f The path of the data file
         * @param c The name of the country
         */
        @JvmStatic
        fun create (f : File, c: String) {
            val data = JSONObject(JSONTokener(FileReader(f))).toMap()
            val streetAbr = HashMap<String, String>()
            val secondaryUnits = HashMap<String, String>()
            val states = HashMap<String, String>()
            val directions = HashMap<String, String>()
            val miscellaneous = HashMap<String, String>()
            val csl = HashMap<String, String>()
            if (data["street_abbreviations"] != null) {
                val m : Map<String, List<String>> = data["street_abbreviations"] as Map<String, List<String>>
                for (key in m.keys) {
                    streetAbr[key] = key
                    for (value in m[key]!!) {
                        streetAbr[value.uppercase()] = key
                    }
                }
            }

            if (data["secondary_units"] != null) {
                val m : Map<String, String> = data["secondary_units"] as Map<String, String>
                for (key in m.keys) {
                    secondaryUnits[key] = key
                    m[key]?.let { secondaryUnits.put(it.uppercase(), key) }
                }
            }

            if (data["states"] != null) {
                val m : Map<String, String> = data["states"] as Map<String, String>
                for (key in m.keys) {
                    states[key] = key
                    m[key]?.let { states.put(it.uppercase(), key) }
                }
            }

            if (data["directions"] != null) {
                val m : Map<String, String> = data["directions"] as Map<String, String>
                for (key in m.keys) {
                    directions[key] = key
                    m[key]?.let { directions.put(it.uppercase(), key) }
                }
            }

            if (data["miscellaneous"] != null) {
                val m : Map<String, List<String>> = data["miscellaneous"] as Map<String, List<String>>
                for (key in m.keys) {
                    miscellaneous[key] = key
                    for (value in m[key]!!) {
                        miscellaneous[value.uppercase()] = key
                    }
                }
            }

            if (data["county_state_localhwy"] != null) {
                val m : Map<String, List<String>> = data["county_state_localhwy"] as Map<String, List<String>>
                for (key in m.keys) {
                    csl[key] = key
                    for (value in m[key]!!) {
                        csl[value.uppercase()] = key
                    }
                }
            }

            val mdl = Model(streetAbr, secondaryUnits, states, directions, miscellaneous, csl)
            File("./models").mkdir()
            ObjectOutputStream(FileOutputStream("./models/" + c + ".model")).use{ it -> it.writeObject(mdl)}
        }

        /**
         * Loads a model
         * @param m Model name
         * @return Model
         */
        @JvmStatic
        fun load(m: String): Model {
//            val f = File("./models/$m.model")
//            if (!f.exists()) throw ModelNotFoundException("Model \"$m\" could not be found")
            try {
                ObjectInputStream(FileInputStream("models/$m.model")).use { it ->
                    val model = it.readObject()
                    when (model) {
                        is Model -> return model
                        else -> throw InvalidModelException("Model is corrupted, please remake model!")
                    }
                }
            } catch (e : Exception) {
                e.printStackTrace()
                throw InvalidModelException("Model is corrupted, please remake model!")

            }

        }
    }
}