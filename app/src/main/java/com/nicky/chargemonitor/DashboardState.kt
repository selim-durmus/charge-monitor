package com.nicky.chargemonitor

import com.nicky.chargemonitor.battery.BatteryReading
import com.nicky.chargemonitor.session.ChargingSession
import com.nicky.chargemonitor.session.ChargingSessionSummary

const val SAMPLE_INTERVAL_MILLIS = 250L
const val MAX_GRAPH_SAMPLES = 120

data class PowerPoint(
    val elapsedRealtimeMillis: Long,
    val watts: Double,
)

data class DashboardState(
    val reading: BatteryReading? = null,
    val powerHistory: List<PowerPoint> = emptyList(),
    val isSampling: Boolean = false,
    val sampleIntervalMillis: Long = SAMPLE_INTERVAL_MILLIS,
    val sessionSummaries: List<ChargingSessionSummary> = emptyList(),
    val activeSessionId: String? = null,
    val selectedSession: ChargingSession? = null,
) {
    fun withReading(newReading: BatteryReading): DashboardState {
        val newPoint = newReading.powerWatts?.let {
            PowerPoint(
                elapsedRealtimeMillis = newReading.elapsedRealtimeMillis,
                watts = it,
            )
        }
        val newHistory = if (newPoint == null) {
            powerHistory
        } else {
            (powerHistory + newPoint).takeLast(MAX_GRAPH_SAMPLES)
        }
        return copy(reading = newReading, powerHistory = newHistory)
    }
}
