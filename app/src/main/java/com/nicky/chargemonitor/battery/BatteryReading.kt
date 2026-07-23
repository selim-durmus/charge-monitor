package com.nicky.chargemonitor.battery

import kotlin.math.abs

enum class ChargeState(val label: String) {
    CHARGING("Charging"),
    FULL("Charged"),
    DISCHARGING("On battery"),
    NOT_CHARGING("Not charging"),
    UNKNOWN("Checking status"),
}

enum class PowerSource(val label: String) {
    AC("Wired · AC"),
    USB("Wired · USB"),
    WIRELESS("Wireless"),
    DOCK("Dock"),
    NONE("Not connected"),
    UNKNOWN("Power connected"),
}

enum class BatteryHealth(val label: String) {
    GOOD("Good"),
    COLD("Cold"),
    OVERHEAT("Overheating"),
    OVER_VOLTAGE("Over voltage"),
    DEAD("Replace battery"),
    FAILURE("Failure"),
    UNKNOWN("Unknown"),
}

enum class ChargingProfile(
    val label: String,
    val description: String,
) {
    STANDARD("Standard", "Default system charging"),
    TEMPERATURE_COLD("Temperature protection", "Charging limited: battery cold"),
    TEMPERATURE_HOT("Temperature protection", "Charging limited: battery warm"),
    ADAPTIVE("Adaptive", "Schedule-aware charging"),
    LONG_LIFE("Long-life", "Charge-limit battery protection"),
    SYSTEM_MANAGED("System managed", "Device-selected charging policy"),
}

enum class ThermalStatus(
    val label: String,
    val severity: Int,
) {
    NONE("No throttling", 0),
    LIGHT("Light throttling", 1),
    MODERATE("Moderate throttling", 2),
    SEVERE("Severe throttling", 3),
    CRITICAL("Critical throttling", 4),
    EMERGENCY("Emergency throttling", 5),
    SHUTDOWN("Shutdown imminent", 6),
    UNKNOWN("Unavailable", -1),
}

data class BatteryReading(
    val elapsedRealtimeMillis: Long,
    val chargeState: ChargeState,
    val powerSource: PowerSource,
    val levelPercent: Int?,
    val voltageMillivolts: Int?,
    val currentMicroamps: Int?,
    val averageCurrentMicroamps: Int?,
    val temperatureTenthsCelsius: Int?,
    val health: BatteryHealth,
    val chargeCounterMicroampHours: Int?,
    val energyCounterNanowattHours: Long?,
    val chargeTimeRemainingMillis: Long?,
    val cycleCount: Int?,
    val technology: String?,
    val chargingProfile: ChargingProfile?,
    val sourceMaxCurrentMicroamps: Int?,
    val sourceMaxVoltageMicrovolts: Int?,
    val thermalStatus: ThermalStatus,
    val thermalHeadroomForecast30Seconds: Double?,
) {
    val voltageVolts: Double?
        get() = voltageMillivolts?.div(1_000.0)

    val currentAmps: Double?
        get() = currentMicroamps?.div(1_000_000.0)

    /** Net battery-side power. Positive is into the battery; negative is out. */
    val powerWatts: Double?
        get() {
            val millivolts = voltageMillivolts ?: return null
            val microamps = currentMicroamps ?: return null
            return millivolts.toDouble() * microamps.toDouble() / 1_000_000_000.0
        }

    val powerMagnitudeWatts: Double?
        get() = powerWatts?.let(::abs)

    val temperatureCelsius: Double?
        get() = temperatureTenthsCelsius?.div(10.0)

    val chargeCounterMilliampHours: Double?
        get() = chargeCounterMicroampHours?.div(1_000.0)

    val energyCounterWattHours: Double?
        get() = energyCounterNanowattHours?.div(1_000_000_000.0)

    /** Optional charger capability advertised by the system, not measured battery power. */
    val sourceCeilingWatts: Double?
        get() {
            val microamps = sourceMaxCurrentMicroamps ?: return null
            val microvolts = sourceMaxVoltageMicrovolts ?: return null
            if (microamps <= 0 || microvolts <= 0) return null
            return microamps.toDouble() * microvolts.toDouble() / 1_000_000_000_000.0
        }

    val isPowerConnected: Boolean
        get() = powerSource != PowerSource.NONE
}
