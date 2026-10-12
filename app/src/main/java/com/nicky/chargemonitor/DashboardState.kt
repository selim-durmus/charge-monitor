package com.nicky.chargemonitor

import com.nicky.chargemonitor.battery.BatteryReading
import com.nicky.chargemonitor.accessory.AccessoryBaseline
import com.nicky.chargemonitor.accessory.AccessoryProfile
import com.nicky.chargemonitor.session.ChargingSession
import com.nicky.chargemonitor.session.ChargingSessionSummary
import com.nicky.chargemonitor.session.GuidedChargingTestState
import com.nicky.chargemonitor.session.SessionComparison

const val SAMPLE_INTERVAL_MILLIS = 250L
const val POWER_HISTORY_WINDOW_MILLIS = 30_000L
const val MAX_GRAPH_SAMPLES = (POWER_HISTORY_WINDOW_MILLIS / SAMPLE_INTERVAL_MILLIS).toInt()

data class PowerPoint(
    val elapsedRealtimeMillis: Long,
    val watts: Double,
)

data class DashboardState(
    val reading: BatteryReading? = null,
    val powerHistory: List<PowerPoint> = emptyList(),
    val isSampling: Boolean = false,
    val sessionsLoaded: Boolean = false,
    val sessionSummaries: List<ChargingSessionSummary> = emptyList(),
    val activeSessionId: String? = null,
    val selectedSessionId: String? = null,
    val selectedSession: ChargingSession? = null,
    val currentAccessory: AccessoryProfile? = null,
    val currentAccessoryBaseline: AccessoryBaseline? = null,
    val comparisonSessions: List<ChargingSession> = emptyList(),
    val comparison: SessionComparison? = null,
    val comparisonLoading: Boolean = false,
    val guidedTest: GuidedChargingTestState = GuidedChargingTestState(),
    val extendedRecordingActive: Boolean = false,
) {
    val activeSessionSummary: ChargingSessionSummary?
        get() = activeSessionId?.let { id -> sessionSummaries.firstOrNull { it.id == id } }

    /**
     * Keeps only points from the last [POWER_HISTORY_WINDOW_MILLIS]. The window is time-based
     * rather than count-based so points from before the app was backgrounded fall away
     * instead of being stitched onto fresh readings.
     */
    fun withReading(newReading: BatteryReading): DashboardState {
        val windowStart = newReading.elapsedRealtimeMillis - POWER_HISTORY_WINDOW_MILLIS
        val retained = powerHistory.filter { it.elapsedRealtimeMillis > windowStart }
        val newHistory = newReading.powerWatts?.let { watts ->
            (retained + PowerPoint(newReading.elapsedRealtimeMillis, watts))
                .takeLast(MAX_GRAPH_SAMPLES)
        } ?: retained
        return copy(reading = newReading, powerHistory = newHistory)
    }
}
