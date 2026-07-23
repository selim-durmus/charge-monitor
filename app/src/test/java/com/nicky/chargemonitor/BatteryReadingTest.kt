package com.nicky.chargemonitor

import com.nicky.chargemonitor.battery.BatteryHealth
import com.nicky.chargemonitor.battery.BatteryReading
import com.nicky.chargemonitor.battery.ChargeState
import com.nicky.chargemonitor.battery.ChargingProfile
import com.nicky.chargemonitor.battery.PowerSource
import com.nicky.chargemonitor.battery.ThermalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryReadingTest {
    @Test
    fun `power is calculated in watts from millivolts and microamps`() {
        val reading = sampleReading(
            voltageMillivolts = 4_200,
            currentMicroamps = 2_000_000,
        )

        assertEquals(8.4, reading.powerWatts ?: error("No power"), 0.0001)
    }

    @Test
    fun `negative current produces outbound battery power`() {
        val reading = sampleReading(
            voltageMillivolts = 4_000,
            currentMicroamps = -500_000,
        )

        assertEquals(-2.0, reading.powerWatts ?: error("No power"), 0.0001)
        assertEquals(2.0, reading.powerMagnitudeWatts ?: error("No magnitude"), 0.0001)
    }

    @Test
    fun `missing current keeps calculated power unavailable`() {
        val reading = sampleReading(currentMicroamps = null)

        assertNull(reading.powerWatts)
    }

    @Test
    fun `dashboard retains thirty seconds of four hertz samples`() {
        val finalState = (0 until MAX_GRAPH_SAMPLES + 10).fold(DashboardState()) { state, index ->
            state.withReading(
                sampleReading(
                    elapsedRealtimeMillis = index * SAMPLE_INTERVAL_MILLIS,
                    currentMicroamps = 1_000_000 + index,
                ),
            )
        }

        assertEquals(MAX_GRAPH_SAMPLES, finalState.powerHistory.size)
        assertEquals(10 * SAMPLE_INTERVAL_MILLIS, finalState.powerHistory.first().elapsedRealtimeMillis)
    }

    @Test
    fun `source ceiling is separate from measured battery power`() {
        val reading = sampleReading(
            voltageMillivolts = 4_200,
            currentMicroamps = 2_000_000,
        )

        assertEquals(8.4, reading.powerWatts ?: error("No measured power"), 0.0001)
        assertEquals(14.994, reading.sourceCeilingWatts ?: error("No source ceiling"), 0.0001)
    }
}

private fun sampleReading(
    elapsedRealtimeMillis: Long = 1_000L,
    voltageMillivolts: Int? = 4_000,
    currentMicroamps: Int? = 1_000_000,
): BatteryReading = BatteryReading(
    elapsedRealtimeMillis = elapsedRealtimeMillis,
    chargeState = ChargeState.CHARGING,
    powerSource = PowerSource.USB,
    levelPercent = 50,
    voltageMillivolts = voltageMillivolts,
    currentMicroamps = currentMicroamps,
    averageCurrentMicroamps = currentMicroamps,
    temperatureTenthsCelsius = 300,
    health = BatteryHealth.GOOD,
    chargeCounterMicroampHours = 2_500_000,
    energyCounterNanowattHours = 10_000_000_000L,
    chargeTimeRemainingMillis = 3_600_000L,
    cycleCount = 100,
    technology = "Li-ion",
    chargingProfile = ChargingProfile.LONG_LIFE,
    sourceMaxCurrentMicroamps = 1_071_000,
    sourceMaxVoltageMicrovolts = 14_000_000,
    thermalStatus = ThermalStatus.NONE,
    thermalHeadroomForecast30Seconds = null,
)
