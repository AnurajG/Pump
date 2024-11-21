package com.eomma.pump

class PumpCharacteristics {
    // Basic states
    var isPowerOn: Boolean = false
    var isPlaying: Boolean = false
    var currentLevel: Int = 1
    var batteryLevel: Int = 0
    var runTime: Int = 0 // in minutes
    var memoryFunctionEnabled: Boolean = false
    var shutdownTime: Int = 30 // default 30 minutes

    // Mode settings
    enum class PumpMode {
        LACTATION,
        BREAST_SUCKING,
        SUCK,
        MASSAGE,
        NIPPLE_TRACTION,
        NOSE_SUCKING;

        companion object {
            fun fromDpsValue(value: String): PumpMode {
                return when (value) {
                    "lactation" -> LACTATION
                    "breast_sucking" -> BREAST_SUCKING
                    "suck" -> SUCK
                    "massage" -> MASSAGE
                    "nipple_traction" -> NIPPLE_TRACTION
                    "nose_sucking" -> NOSE_SUCKING
                    else -> BREAST_SUCKING // Default mode
                }
            }

            fun toDpsValue(mode: PumpMode): String {
                return when (mode) {
                    LACTATION -> "lactation"
                    BREAST_SUCKING -> "breast_sucking"
                    SUCK -> "suck"
                    MASSAGE -> "massage"
                    NIPPLE_TRACTION -> "nipple_traction"
                    NOSE_SUCKING -> "nose_sucking"
                }
            }
        }
    }

    var currentMode: PumpMode = PumpMode.BREAST_SUCKING

    // Constants
    companion object {
        const val MAX_LEVEL = 12
        const val MIN_LEVEL = 1
        const val MIN_SHUTDOWN_TIME = 5
        const val MAX_SHUTDOWN_TIME = 60

        // DPS IDs
        const val DPS_SWITCH = "1"
        const val DPS_START = "2"
        const val DPS_MODE = "4"
        const val DPS_LACTATION = "5"
        const val DPS_BREAST_SUCKING = "6"
        const val DPS_SUCK = "7"
        const val DPS_MASSAGE = "12"
        const val DPS_RUN_TIME = "13"
        const val DPS_MEMORY = "14"
        const val DPS_BATTERY = "15"
        const val DPS_SHUTDOWN_TIME = "101"
    }

    // Update from DPS Map
    fun updateFromDpsMap(dpsMap: HashMap<String, Any>?) {
        dpsMap?.forEach { (dpsId, value) ->
            try {
                when (dpsId) {
                    DPS_SWITCH -> isPowerOn = value.toString().toBoolean()
                    DPS_START -> isPlaying = !value.toString().toBoolean()
                    DPS_MODE -> {
                        currentMode = PumpMode.fromDpsValue(value.toString())
                    }
                    DPS_LACTATION, DPS_BREAST_SUCKING, DPS_SUCK, DPS_MASSAGE -> {
                        if (isDpsIdMatchingCurrentMode(dpsId)) {
                            val level = when (value) {
                                is Int -> value
                                is String -> value.toInt()
                                else -> return@forEach
                            }
                            currentLevel = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
                        }
                    }
                    DPS_RUN_TIME -> {
                        val time = when (value) {
                            is Int -> value
                            is String -> value.toInt()
                            else -> return@forEach
                        }
                        runTime = time.coerceIn(0, 60)
                    }
                    DPS_MEMORY -> memoryFunctionEnabled = value.toString().toBoolean()
                    DPS_BATTERY -> {
                        val battery = when (value) {
                            is Int -> value
                            is String -> value.toInt()
                            else -> return@forEach
                        }
                        batteryLevel = battery.coerceIn(0, 100)
                    }
                    DPS_SHUTDOWN_TIME -> {
                        val time = when (value) {
                            is Int -> value
                            is String -> value.toInt()
                            else -> return@forEach
                        }
                        shutdownTime = time.coerceIn(MIN_SHUTDOWN_TIME, MAX_SHUTDOWN_TIME)
                    }
                }
            } catch (e: Exception) {
                // Handle parsing errors silently to prevent crashes
                e.printStackTrace()
            }
        }
    }

    // Update single DPS value
    fun updateFromDps(dpsId: String, value: Any) {
        val singleValueMap = HashMap<String, Any>()
        singleValueMap[dpsId] = value
        updateFromDpsMap(singleValueMap)
    }

    private fun isDpsIdMatchingCurrentMode(dpsId: String): Boolean {
        return when (currentMode) {
            PumpMode.LACTATION -> dpsId == DPS_LACTATION
            PumpMode.BREAST_SUCKING -> dpsId == DPS_BREAST_SUCKING
            PumpMode.SUCK -> dpsId == DPS_SUCK
            PumpMode.MASSAGE -> dpsId == DPS_MASSAGE
            else -> false
        }
    }

    // Generate DPS commands
    fun generateDpsCommand(dpsId: String): String {
        val value = when (dpsId) {
            DPS_SWITCH -> isPowerOn
            DPS_START -> !isPlaying
            DPS_MODE -> PumpMode.toDpsValue(currentMode)
            DPS_LACTATION, DPS_BREAST_SUCKING, DPS_SUCK, DPS_MASSAGE -> currentLevel
            DPS_MEMORY -> memoryFunctionEnabled
            DPS_SHUTDOWN_TIME -> shutdownTime
            else -> return "" // Invalid DPS ID
        }

        if(dpsId== DPS_MODE)
            return """{"$dpsId": "$value"}"""
        return """{"$dpsId": $value}"""
    }

    // Helper methods for level control
    fun incrementLevel(): Boolean {
        if (currentLevel < MAX_LEVEL) {
            currentLevel++
            return true
        }
        return false
    }

    fun decrementLevel(): Boolean {
        if (currentLevel > MIN_LEVEL) {
            currentLevel--
            return true
        }
        return false
    }

    fun setLevel(level: Int) {
        currentLevel = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
    }

    // Battery status helpers
    fun getBatteryPercentage(): Int = batteryLevel

    fun getBatteryStatus(): String {
        return when {
            batteryLevel >= 80 -> "High"
            batteryLevel >= 20 -> "Medium"
            else -> "Low"
        }
    }

    // Mode helpers
    fun setMode(mode: PumpMode) {
        currentMode = mode
    }

    fun getCurrentLevelDpsCommand(): String {
        val dpsId = when (currentMode) {
            PumpMode.LACTATION -> DPS_LACTATION
            PumpMode.BREAST_SUCKING -> DPS_BREAST_SUCKING
            PumpMode.SUCK -> DPS_SUCK
            PumpMode.MASSAGE -> DPS_MASSAGE
            PumpMode.NIPPLE_TRACTION,
            PumpMode.NOSE_SUCKING -> DPS_SUCK // fallback to SUCK for these modes
        }
        return """{"$dpsId": $currentLevel}"""
    }

    fun getModeDisplayName(): String {
        return when (currentMode) {
            PumpMode.LACTATION -> "Stimulation"
            PumpMode.BREAST_SUCKING -> "Expression"
            PumpMode.SUCK -> "Automatic"
            PumpMode.MASSAGE -> "Massage"
            PumpMode.NIPPLE_TRACTION -> "Nipple Traction"
            PumpMode.NOSE_SUCKING -> "Nose Suction"
        }
    }
}