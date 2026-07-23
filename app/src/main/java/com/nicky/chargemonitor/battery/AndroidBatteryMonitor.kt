package com.nicky.chargemonitor.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock

class AndroidBatteryMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val batteryManager = appContext.getSystemService(BatteryManager::class.java)
    private val powerManager = appContext.getSystemService(PowerManager::class.java)
    private var lastThermalHeadroomReadAtMillis = Long.MIN_VALUE
    private var cachedThermalHeadroomForecast: Double? = null

    @Suppress("DEPRECATION")
    @Synchronized
    fun read(): BatteryReading {
        val elapsedRealtimeMillis = SystemClock.elapsedRealtime()
        val batteryIntent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )

        val level = batteryIntent?.intExtraOrNull(BatteryManager.EXTRA_LEVEL)
        val scale = batteryIntent?.intExtraOrNull(BatteryManager.EXTRA_SCALE)
        val levelPercent = if (level != null && scale != null && scale > 0) {
            ((level.toDouble() / scale.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } else {
            null
        }

        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN,
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val temperature = batteryIntent?.intExtraOrNull(BatteryManager.EXTRA_TEMPERATURE)
            ?.takeIf { it > -1_000 }
        val voltage = batteryIntent?.intExtraOrNull(BatteryManager.EXTRA_VOLTAGE)
            ?.takeIf { it > 0 }
        val technology = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?.takeIf { it.isNotBlank() }

        return BatteryReading(
            elapsedRealtimeMillis = elapsedRealtimeMillis,
            chargeState = status.toChargeState(),
            powerSource = plugged.toPowerSource(),
            levelPercent = levelPercent,
            voltageMillivolts = voltage,
            currentMicroamps = intProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            averageCurrentMicroamps = intProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),
            temperatureTenthsCelsius = temperature,
            health = (batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_HEALTH,
                BatteryManager.BATTERY_HEALTH_UNKNOWN,
            ) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN).toBatteryHealth(),
            chargeCounterMicroampHours = intProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            energyCounterNanowattHours = longProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER),
            chargeTimeRemainingMillis = batteryManager.computeChargeTimeRemaining()
                .takeIf { it >= 0L },
            cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                batteryIntent?.intExtraOrNull(BatteryManager.EXTRA_CYCLE_COUNT)
                    ?.takeIf { it >= 0 }
            } else {
                null
            },
            technology = technology,
            chargingProfile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                batteryIntent
                    ?.intExtraOrNull(BatteryManager.EXTRA_CHARGING_STATUS)
                    ?.toChargingProfile()
            } else {
                null
            },
            sourceMaxCurrentMicroamps = batteryIntent
                ?.intExtraOrNull(EXTRA_MAX_CHARGING_CURRENT)
                ?.takeIf { it > 0 },
            sourceMaxVoltageMicrovolts = batteryIntent
                ?.intExtraOrNull(EXTRA_MAX_CHARGING_VOLTAGE)
                ?.takeIf { it > 0 },
            thermalStatus = powerManager.currentThermalStatus.toThermalStatus(),
            thermalHeadroomForecast30Seconds = thermalHeadroomForecast(
                elapsedRealtimeMillis = elapsedRealtimeMillis,
            ),
        )
    }

    private fun thermalHeadroomForecast(elapsedRealtimeMillis: Long): Double? {
        if (lastThermalHeadroomReadAtMillis == Long.MIN_VALUE ||
            elapsedRealtimeMillis - lastThermalHeadroomReadAtMillis >=
                THERMAL_HEADROOM_INTERVAL_MILLIS
        ) {
            lastThermalHeadroomReadAtMillis = elapsedRealtimeMillis
            runCatching {
                powerManager.getThermalHeadroom(THERMAL_FORECAST_SECONDS)
            }.getOrNull()
                ?.takeIf { it.isFinite() }
                ?.toDouble()
                ?.let { cachedThermalHeadroomForecast = it }
        }
        return cachedThermalHeadroomForecast
    }

    private fun intProperty(property: Int): Int? =
        batteryManager.getIntProperty(property).takeUnless { it == Int.MIN_VALUE }

    private fun longProperty(property: Int): Long? =
        batteryManager.getLongProperty(property).takeUnless { it == Long.MIN_VALUE }

    private fun Intent.intExtraOrNull(name: String): Int? =
        getIntExtra(name, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE }
}

// These extras are present in AOSP's battery broadcast but are not guaranteed
// for third-party apps, so they are treated as optional capability hints.
private const val EXTRA_MAX_CHARGING_CURRENT = "max_charging_current"
private const val EXTRA_MAX_CHARGING_VOLTAGE = "max_charging_voltage"
private const val THERMAL_HEADROOM_INTERVAL_MILLIS = 1_000L
private const val THERMAL_FORECAST_SECONDS = 30

private fun Int.toChargeState(): ChargeState = when (this) {
    BatteryManager.BATTERY_STATUS_CHARGING -> ChargeState.CHARGING
    BatteryManager.BATTERY_STATUS_FULL -> ChargeState.FULL
    BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeState.DISCHARGING
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeState.NOT_CHARGING
    else -> ChargeState.UNKNOWN
}

private fun Int.toPowerSource(): PowerSource = when {
    this == 0 -> PowerSource.NONE
    this and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> PowerSource.WIRELESS
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        this and BatteryManager.BATTERY_PLUGGED_DOCK != 0 -> PowerSource.DOCK
    this and BatteryManager.BATTERY_PLUGGED_AC != 0 -> PowerSource.AC
    this and BatteryManager.BATTERY_PLUGGED_USB != 0 -> PowerSource.USB
    else -> PowerSource.UNKNOWN
}

private fun Int.toBatteryHealth(): BatteryHealth = when (this) {
    BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
    BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
    BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
    else -> BatteryHealth.UNKNOWN
}

/** Values mirror Android's BatteryChargingStatus enum used by EXTRA_CHARGING_STATUS. */
private fun Int.toChargingProfile(): ChargingProfile = when (this) {
    1 -> ChargingProfile.STANDARD
    2 -> ChargingProfile.TEMPERATURE_COLD
    3 -> ChargingProfile.TEMPERATURE_HOT
    4 -> ChargingProfile.LONG_LIFE
    5 -> ChargingProfile.ADAPTIVE
    else -> ChargingProfile.SYSTEM_MANAGED
}

private fun Int.toThermalStatus(): ThermalStatus = when (this) {
    PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
    PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
    PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
    PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
    PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
    PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
    PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
    else -> ThermalStatus.UNKNOWN
}
