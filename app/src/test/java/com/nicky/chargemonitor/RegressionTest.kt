package com.nicky.chargemonitor

import com.nicky.chargemonitor.battery.BatteryHealth
import com.nicky.chargemonitor.battery.BatteryReading
import com.nicky.chargemonitor.battery.ChargeState
import com.nicky.chargemonitor.battery.ChargingProfile
import com.nicky.chargemonitor.battery.PowerSource
import com.nicky.chargemonitor.battery.ThermalStatus
import com.nicky.chargemonitor.session.ChargingSession
import com.nicky.chargemonitor.session.DiagnosticSample
import com.nicky.chargemonitor.session.GUIDED_TEST_WINDOW_MILLIS
import com.nicky.chargemonitor.session.GuidedChargingTestState
import com.nicky.chargemonitor.session.GuidedTestPhase
import com.nicky.chargemonitor.session.SessionAnalyzer
import com.nicky.chargemonitor.session.SessionComparator
import com.nicky.chargemonitor.session.accessoryBaselinesFor
import com.nicky.chargemonitor.session.advance
import com.nicky.chargemonitor.session.arm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegressionTest {
    @Test
    fun `power history drops points from before a pause instead of stitching them on`() {
        val beforePause = (0 until 40).fold(DashboardState()) { state, index ->
            state.withReading(reading(elapsedRealtimeMillis = index * SAMPLE_INTERVAL_MILLIS))
        }
        val afterPause = beforePause.withReading(reading(elapsedRealtimeMillis = 120_000L))

        assertEquals(1, afterPause.powerHistory.size)
        assertEquals(120_000L, afterPause.powerHistory.single().elapsedRealtimeMillis)
    }

    @Test
    fun `guided window closes itself after five minutes`() {
        val running = GuidedChargingTestState(phase = GuidedTestPhase.WAITING_FOR_A)
            .advance(isPowerConnected = true, activeSessionId = "s1", timestampMillis = 0L)
        assertEquals(GuidedTestPhase.RUNNING_A, running.phase)

        val stillRunning = running.advance(true, "s1", GUIDED_TEST_WINDOW_MILLIS - 1)
        assertEquals(GuidedTestPhase.RUNNING_A, stillRunning.phase)

        val closed = running.advance(true, "s1", GUIDED_TEST_WINDOW_MILLIS + 4_000L)
        assertEquals(GuidedTestPhase.PREPARE_B, closed.phase)
        // The window is capped at exactly five minutes even if the tick arrives late.
        assertEquals(GUIDED_TEST_WINDOW_MILLIS, closed.firstRun?.endedAtMillis)
    }

    @Test
    fun `guided window closes when the charger is unplugged`() {
        val running = GuidedChargingTestState(phase = GuidedTestPhase.WAITING_FOR_B)
            .advance(true, "s2", timestampMillis = 1_000L)
        val closed = running.advance(false, null, timestampMillis = 61_000L)

        assertEquals(GuidedTestPhase.COMPLETE, closed.phase)
        assertEquals(61_000L, closed.secondRun?.endedAtMillis)
    }

    @Test
    fun `guided test can only be armed while unplugged, including after process restore`() {
        assertEquals(
            GuidedTestPhase.PREPARE_A,
            GuidedChargingTestState(GuidedTestPhase.PREPARE_A).arm(isPowerConnected = true).phase,
        )
        assertEquals(
            GuidedTestPhase.WAITING_FOR_A,
            GuidedChargingTestState(GuidedTestPhase.IDLE).arm(isPowerConnected = false).phase,
        )
    }

    @Test
    fun `baseline counts only sessions that contributed comparable samples`() {
        val good = session("good", accessoryId = "pad", power = 10.0, level = 40)
        val tooFull = session("full", accessoryId = "pad", power = 3.0, level = 90)

        val baseline = accessoryBaselinesFor(listOf(good, tooFull))["pad"]

        assertEquals(1, baseline?.sessionCount)
        assertEquals(10.0, baseline?.medianPowerWatts ?: error("No baseline"), 0.001)
    }

    @Test
    fun `baseline needs enough comparable buckets`() {
        val short = session("short", accessoryId = "pad", power = 10.0, level = 40, seconds = 40)

        assertNull(accessoryBaselinesFor(listOf(short))["pad"])
    }

    @Test
    fun `phone held at full on the charger is not reported as draining`() {
        val full = session(
            "full",
            power = -0.4,
            level = 100,
            chargeState = ChargeState.FULL,
        )

        assertEquals("Battery was full", SessionAnalyzer.summarize(full).diagnosis.title)
    }

    @Test
    fun `comparison explains why confidence was reduced`() {
        val wired = session("a", power = 10.0, level = 40, source = PowerSource.USB)
        val wireless = session("b", power = 6.0, level = 40, source = PowerSource.WIRELESS)

        val comparison = SessionComparator.compare(wired, wireless)

        assertEquals(8, comparison.confidenceScore)
        assertTrue(comparison.confidenceNotes.single().contains("connection types"))
    }
}

private fun reading(elapsedRealtimeMillis: Long) = BatteryReading(
    elapsedRealtimeMillis = elapsedRealtimeMillis,
    chargeState = ChargeState.CHARGING,
    powerSource = PowerSource.USB,
    levelPercent = 50,
    voltageMillivolts = 4_000,
    currentMicroamps = 1_000_000,
    averageCurrentMicroamps = 1_000_000,
    temperatureTenthsCelsius = 300,
    health = BatteryHealth.GOOD,
    chargeCounterMicroampHours = null,
    energyCounterNanowattHours = null,
    chargeTimeRemainingMillis = null,
    cycleCount = null,
    technology = null,
    chargingProfile = ChargingProfile.STANDARD,
    sourceMaxCurrentMicroamps = null,
    sourceMaxVoltageMicrovolts = null,
    thermalStatus = ThermalStatus.NONE,
    thermalHeadroomForecast30Seconds = null,
)

private fun session(
    id: String,
    power: Double,
    level: Int,
    accessoryId: String? = null,
    seconds: Int = 120,
    chargeState: ChargeState = ChargeState.CHARGING,
    source: PowerSource = PowerSource.USB,
): ChargingSession {
    val startedAt = 1_000_000L
    return ChargingSession(
        id = id,
        startedAtMillis = startedAt,
        endedAtMillis = startedAt + seconds * 1_000L,
        joinedInProgress = false,
        endReason = null,
        initialSource = source,
        accessoryId = accessoryId,
        accessoryName = accessoryId,
        samples = (1..seconds).map { second ->
            DiagnosticSample(
                timestampMillis = startedAt + second * 1_000L,
                elapsedMillis = second * 1_000L,
                powerMinimumWatts = power - 0.1,
                powerAverageWatts = power,
                powerMaximumWatts = power + 0.1,
                currentMinimumAmps = null,
                currentAverageAmps = null,
                currentMaximumAmps = null,
                systemAverageCurrentAmps = null,
                voltageVolts = 4.0,
                levelPercent = level,
                temperatureCelsius = 30.0,
                thermalStatus = ThermalStatus.NONE,
                thermalHeadroomForecast30Seconds = 0.2,
                chargeState = chargeState,
                powerSource = source,
                chargingProfile = ChargingProfile.STANDARD,
                sourceCeilingWatts = null,
                chargeCounterMilliampHours = null,
                energyCounterWattHours = null,
                chargeTimeRemainingMillis = null,
            )
        },
        events = emptyList(),
    )
}
