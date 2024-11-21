package com.eomma.pump

import org.json.JSONObject

/**
 * Helper class to parse DPS (Data Point Specification) strings from Tuya devices
 */
object DpsParser {
    /**
     * Parses a DPS string from the device into a HashMap
     * Example input: {"1": true, "2": false, "4": 1, "15": 85}
     *
     * @param dpStr The DPS string in JSON format
     * @return HashMap containing the parsed DPS values, or null if parsing fails
     */
    fun parseDpsString(dpStr: String): HashMap<String, Any>? {
        return try {
            val dpsMap = HashMap<String, Any>()
            val jsonObject = JSONObject(dpStr)

            // Iterate through all keys in the JSON object
            jsonObject.keys().forEach { key ->
                val value = when (val jsonValue = jsonObject.get(key)) {
                    is Boolean -> jsonValue
                    is Int -> jsonValue
                    is Long -> jsonValue.toInt()
                    is Double -> jsonValue.toInt()
                    is String -> {
                        // Try to parse string as various types
                        when {
                            jsonValue.equals("true", ignoreCase = true) -> true
                            jsonValue.equals("false", ignoreCase = true) -> false
                            jsonValue.toIntOrNull() != null -> jsonValue.toInt()
                            else -> jsonValue
                        }
                    }
                    else -> jsonValue.toString()
                }
                dpsMap[key] = value
            }
            dpsMap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Validates if a DPS string has valid JSON format
     *
     * @param dpStr The DPS string to validate
     * @return true if the string is valid JSON, false otherwise
     */
    fun isValidDpsString(dpStr: String): Boolean {
        return try {
            JSONObject(dpStr)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extracts a single DPS value from a DPS string
     *
     * @param dpStr The DPS string in JSON format
     * @param dpsId The DPS ID to extract
     * @return The value for the specified DPS ID, or null if not found
     */
    fun extractDpsValue(dpStr: String, dpsId: String): Any? {
        return try {
            val jsonObject = JSONObject(dpStr)
            when (val value = jsonObject.opt(dpsId)) {
                is Boolean -> value
                is Int -> value
                is Long -> value.toInt()
                is Double -> value.toInt()
                is String -> {
                    when {
                        value.equals("true", ignoreCase = true) -> true
                        value.equals("false", ignoreCase = true) -> false
                        value.toIntOrNull() != null -> value.toInt()
                        else -> value
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates a DPS string from a map of values
     *
     * @param dpsMap The map of DPS ID to value pairs
     * @return A JSON formatted DPS string
     */
    fun generateDpsString(dpsMap: Map<String, Any>): String {
        val jsonObject = JSONObject()
        dpsMap.forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        return jsonObject.toString()
    }
}