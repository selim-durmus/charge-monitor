package com.nicky.chargemonitor

import com.nicky.chargemonitor.accessory.accessorySignature
import com.nicky.chargemonitor.battery.BatteryHealth
import com.nicky.chargemonitor.battery.BatteryReading
import com.nicky.chargemonitor.battery.ChargeState
import com.nicky.chargemonitor.battery.ChargingProfile
import com.nicky.chargemonitor.battery.PowerSource
import com.nicky.chargemonitor.battery.ThermalStatus
import com.nicky.chargemonitor.session.ChargingSession
import com.nicky.chargemonitor.session.DiagnosticSample
import com.nicky.chargemonitor.session.GuidedTestRun
import com.nicky.chargemonitor.session.SessionAnalyzer
import com.nicky.chargemonitor.session.SessionComparator
import com.nicky.chargemonitor.session.sliceForTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticFeaturesTest {
    @Test
    fun `accessory signature normalizes reported source limits`() {
        val first = reading(
            sourceMaxCurrentMicroamps = 1_071_000,
            sourceMaxVoltageMicrovolts = 9_041_000,
        )
        val second = reading(
            sourceMaxCurrentMicroamps = 1_074_000,
            sourceMaxVoltageMicrovolts = 9_049_000,
        )

        assertEquals(first.accessorySignature(), second.accessorySignature())
        assertEquals("USB:1050000:9000000", first.accessorySignature())
    }

    @Test
    fun `disconnected reading has no accessory signature`() {
        assertNull(reading(powerSource = PowerSource.NONE).accessorySignature())
    }

    @Test
    fun `comparison excludes warmup and compares common battery range`() {
        val first = session(
            id = "a",
            powerWatts = 10.0,
            startLevel = 50,
            accessoryName = "Charger A",
        )
        val second = session(
            id = "b",
            powerWatts = 7.0,
            startLevel = 50,
            accessoryName = "Charger B",
        )

        val comparison = SessionComparator.compare(first, second)

        assertEquals(10.0, comparison.first.averagePowerWatts ?: error("No average"), 0.001)
        assertEquals(7.0, comparison.second.averagePowerWatts ?: error("No average"), 0.001)
        assertEquals(3.0, comparison.averagePowerDeltaWatts ?: error("No delta"), 0.001)
        assertTrue(comparison.headline.contains("Charger A"))
        assertEquals(10, comparison.confidenceScore)
    }

    @Test
    fun `guided test slice keeps only captured time window`() {
        val session = session(
            id = "slice",
            powerWatts = 8.0,
            startLevel = 40,
        )
        val run = GuidedTestRun(
            sessionId = session.id,
            startedAtMillis = session.startedAtMillis + 40_000L,
            endedAtMillis = session.startedAtMillis + 60_000L,
        )

        val sliced = session.sliceForTest(run)

        assertEquals(21, sliced.samples.size)
        assertTrue(sliced.samples.all {
            it.timestampMillis in run.startedAtMillis..requireNotNull(run.endedAtMillis)
        })
    }

    @Test
    fun `thermal forecast threshold produces imminent throttling diagnosis`() {
        val session = session(
            id = "thermal",
            powerWatts = 8.0,
            startLevel = 50,
            thermalHeadroom = 1.02,
        )

        assertEquals(
            "Thermal throttling was imminent",
            SessionAnalyzer.summarize(session).diagnosis.title,
        )
    }
}

private fun reading(
    powerSource: PowerSource = PowerSource.USB,
    sourceMaxCurrentMicroamps: Int = 1_071_000,
    sourceMaxVoltageMicrovolts: Int = 9_041_000,
) = BatteryReading(
    elapsedRealtimeMillis = 1_000L,
    chargeState = if (powerSource == PowerSource.NONE) {
        ChargeState.DISCHARGING
    } else {
        ChargeState.CHARGING
    },
    powerSource = powerSource,
    levelPercent = 50,
    voltageMillivolts = 4_000,
    currentMicroamps = 2_000_000,
    averageCurrentMicroamps = 2_000_000,
    temperatureTenthsCelsius = 300,
    health = BatteryHealth.GOOD,
    chargeCounterMicroampHours = 2_500_000,
    energyCounterNanowattHours = 10_000_000_000L,
    chargeTimeRemainingMillis = 3_600_000L,
    cycleCount = 100,
    technology = "Li-ion",
    chargingProfile = ChargingProfile.STANDARD,
    sourceMaxCurrentMicroamps = sourceMaxCurrentMicroamps,
    sourceMaxVoltageMicrovolts = sourceMaxVoltageMicrovolts,
    thermalStatus = ThermalStatus.NONE,
    thermalHeadroomForecast30Seconds = 0.2,
)

private fun session(
    id: String,
    powerWatts: Double,
    startLevel: Int,
    accessoryName: String? = null,
    thermalHeadroom: Double = 0.2,
): ChargingSession {
    val startedAt = 1_000_000L
    return ChargingSession(
        id = id,
        startedAtMillis = startedAt,
        endedAtMillis = startedAt + 100_000L,
        joinedInProgress = false,
        endReason = null,
        initialSource = PowerSource.USB,
        accessoryId = accessoryName?.lowercase(),
        accessoryName = accessoryName,
        samples = (1..100).map { second ->
            DiagnosticSample(
                timestampMillis = startedAt + second * 1_000L,
                elapsedMillis = second * 1_000L,
                powerMinimumWatts = powerWatts - 0.1,
                powerAverageWatts = if (second < 30) 1.0 else powerWatts,
                powerMaximumWatts = powerWatts + 0.1,
                currentMinimumAmps = 1.0,
                currentAverageAmps = 1.1,
                currentMaximumAmps = 1.2,
                systemAverageCurrentAmps = 1.0,
                voltageVolts = 4.0,
                levelPercent = startLevel + second / 50,
                temperatureCelsius = 30.0,
                thermalStatus = ThermalStatus.NONE,
                thermalHeadroomForecast30Seconds = thermalHeadroom,
                chargeState = ChargeState.CHARGING,
                powerSource = PowerSource.USB,
                chargingProfile = ChargingProfile.STANDARD,
                sourceCeilingWatts = 18.0,
                chargeCounterMilliampHours = 2_500.0,
                energyCounterWattHours = 10.0,
                chargeTimeRemainingMillis = 3_600_000L,
            )
        },
        events = emptyList(),
    )
}
