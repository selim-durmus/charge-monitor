package com.nicky.chargemonitor.accessory

import com.nicky.chargemonitor.battery.BatteryReading
import com.nicky.chargemonitor.battery.PowerSource

data class AccessoryProfile(
    val id: String,
    val signature: String,
    val name: String,
    val powerSource: PowerSource,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class AccessoryBaseline(
    val accessoryId: String,
    val sessionCount: Int,
    val comparableBucketCount: Int,
    val averagePowerWatts: Double,
    val medianPowerWatts: Double,
    val tenthPercentilePowerWatts: Double,
    val peakPowerWatts: Double,
)

fun BatteryReading.accessorySignature(): String? {
    if (!isPowerConnected) return null
    val roundedCurrent = sourceMaxCurrentMicroamps?.roundTo(50_000) ?: 0
    val roundedVoltage = sourceMaxVoltageMicrovolts?.roundTo(100_000) ?: 0
    return "${powerSource.name}:$roundedCurrent:$roundedVoltage"
}

private fun Int.roundTo(step: Int): Int =
    ((this + step / 2) / step) * step
