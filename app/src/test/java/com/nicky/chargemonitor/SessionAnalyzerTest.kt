package com.nicky.chargemonitor

import com.nicky.chargemonitor.battery.ChargeState
import com.nicky.chargemonitor.battery.ChargingProfile
import com.nicky.chargemonitor.battery.PowerSource
import com.nicky.chargemonitor.battery.ThermalStatus
import com.nicky.chargemonitor.session.ChargingSession
import com.nicky.chargemonitor.session.DiagnosticSample
import com.nicky.chargemonitor.session.SessionAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAnalyzerTest {
    @Test
    fun `summary calculates power distribution and duration`() {
        val session = sampleSession(powers = (1..10).map(Int::toDouble))

        val summary = SessionAnalyzer.summarize(session)

        assertEquals(5.5, summary.averagePowerWatts ?: error("No average"), 0.001)
        assertEquals(5.0, summary.medianPowerWatts ?: error("No median"), 0.001)
        assertEquals(1.0, summary.tenthPercentilePowerWatts ?: error("No p10"), 0.001)
        assertEquals(10_000L, summary.durationMillis)
        assertEquals(10, summary.sampleCount)
    }

    @Test
    fun `thermal evidence takes precedence in diagnosis`() {
        val session = sampleSession(
            powers = List(12) { 3.0 },
            thermalStatus = ThermalStatus.MODERATE,
            temperatureCelsius = 42.0,
        )

        val diagnosis = SessionAnalyzer.summarize(session).diagnosis

        assertEquals("Heat likely limited charging", diagnosis.title)
    }

    @Test
    fun `missing source ceiling is not called a low capability source`() {
        val session = sampleSession(
            powers = List(12) { 3.0 },
            sourceCeilingWatts = null,
        )

        val diagnosis = SessionAnalyzer.summarize(session).diagnosis

        assertEquals("Low net battery power", diagnosis.title)
        assertTrue(diagnosis.detail.contains("did not expose"))
    }

    @Test
    fun `long life limit near eighty percent takes precedence over warm temperature`() {
        val session = sampleSession(
            powers = List(12) { -1.0 },
            temperatureCelsius = 41.0,
            chargingProfile = ChargingProfile.LONG_LIFE,
            startingLevelPercent = 78,
        )

        val diagnosis = SessionAnalyzer.summarize(session).diagnosis

        assertEquals("Charge limit was active", diagnosis.title)
    }
}

private fun sampleSession(
    powers: List<Double>,
    thermalStatus: ThermalStatus = ThermalStatus.NONE,
    temperatureCelsius: Double = 30.0,
    sourceCeilingWatts: Double? = 15.0,
    chargingProfile: ChargingProfile = ChargingProfile.STANDARD,
    startingLevelPercent: Int = 50,
): ChargingSession = ChargingSession(
    id = "test-session",
    startedAtMillis = 1_000_000L,
    endedAtMillis = 1_000_000L + powers.size * 1_000L,
    joinedInProgress = false,
    endReason = null,
    initialSource = PowerSource.USB,
    accessoryId = null,
    accessoryName = null,
    samples = powers.mapIndexed { index, power ->
        DiagnosticSample(
            timestampMillis = 1_000_000L + (index + 1) * 1_000L,
            elapsedMillis = (index + 1) * 1_000L,
            powerMinimumWatts = power - 0.1,
            powerAverageWatts = power,
            powerMaximumWatts = power + 0.1,
            currentMinimumAmps = 1.0,
            currentAverageAmps = 1.1,
            currentMaximumAmps = 1.2,
            systemAverageCurrentAmps = 1.0,
            voltageVolts = 4.0,
            levelPercent = (startingLevelPercent + index / 6).coerceAtMost(100),
            temperatureCelsius = temperatureCelsius,
            thermalStatus = thermalStatus,
            thermalHeadroomForecast30Seconds = null,
            chargeState = ChargeState.CHARGING,
            powerSource = PowerSource.USB,
            chargingProfile = chargingProfile,
            sourceCeilingWatts = sourceCeilingWatts,
            chargeCounterMilliampHours = 2_500.0,
            energyCounterWattHours = 10.0,
            chargeTimeRemainingMillis = 3_600_000L,
        )
    },
    events = emptyList(),
)
