package com.nicky.chargemonitor.session

import com.nicky.chargemonitor.battery.ChargeState
import com.nicky.chargemonitor.battery.ChargingProfile
import com.nicky.chargemonitor.battery.PowerSource
import com.nicky.chargemonitor.battery.ThermalStatus
import kotlin.math.ceil

const val MAX_STORED_SESSIONS = 5
const val SESSION_BUCKET_MILLIS = 1_000L
const val SESSION_REJOIN_WINDOW_MILLIS = 5 * 60_000L

enum class SessionEndReason(val label: String) {
    UNPLUGGED("Charger disconnected"),
    APP_BACKGROUNDED("App left foreground"),
}

enum class SessionEventType {
    STARTED,
    RESUMED,
    PAUSED,
    ENDED,
    SOURCE_CHANGED,
    PROFILE_CHANGED,
    CHARGE_STATE_CHANGED,
    THERMAL_CHANGED,
}

data class SessionEvent(
    val timestampMillis: Long,
    val type: SessionEventType,
    val label: String,
)

data class DiagnosticSample(
    val timestampMillis: Long,
    val elapsedMillis: Long,
    val powerMinimumWatts: Double?,
    val powerAverageWatts: Double?,
    val powerMaximumWatts: Double?,
    val currentMinimumAmps: Double?,
    val currentAverageAmps: Double?,
    val currentMaximumAmps: Double?,
    val systemAverageCurrentAmps: Double?,
    val voltageVolts: Double?,
    val levelPercent: Int?,
    val temperatureCelsius: Double?,
    val thermalStatus: ThermalStatus,
    val chargeState: ChargeState,
    val powerSource: PowerSource,
    val chargingProfile: ChargingProfile?,
    val sourceCeilingWatts: Double?,
    val chargeCounterMilliampHours: Double?,
    val energyCounterWattHours: Double?,
    val chargeTimeRemainingMillis: Long?,
)

data class ChargingSession(
    val id: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val joinedInProgress: Boolean,
    val endReason: SessionEndReason?,
    val initialSource: PowerSource,
    val samples: List<DiagnosticSample>,
    val events: List<SessionEvent>,
) {
    val isActive: Boolean
        get() = endedAtMillis == null
}

enum class DiagnosisTone {
    GOOD,
    NOTICE,
    WARNING,
}

data class SessionDiagnosis(
    val title: String,
    val detail: String,
    val tone: DiagnosisTone,
)

data class ChargingSessionSummary(
    val id: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val joinedInProgress: Boolean,
    val endReason: SessionEndReason?,
    val source: PowerSource,
    val durationMillis: Long,
    val sampleCount: Int,
    val startLevelPercent: Int?,
    val endLevelPercent: Int?,
    val averagePowerWatts: Double?,
    val medianPowerWatts: Double?,
    val tenthPercentilePowerWatts: Double?,
    val minimumPowerWatts: Double?,
    val maximumPowerWatts: Double?,
    val peakTemperatureCelsius: Double?,
    val thermalLimitedSeconds: Int,
    val chargingProfile: ChargingProfile?,
    val sourceCeilingWatts: Double?,
    val diagnosis: SessionDiagnosis,
) {
    val isActive: Boolean
        get() = endedAtMillis == null
}

object SessionAnalyzer {
    fun summarize(session: ChargingSession): ChargingSessionSummary {
        val samples = session.samples
        val powers = samples.mapNotNull { it.powerAverageWatts }.filter { it.isFinite() }
        val sortedPowers = powers.sorted()
        val averagePower = powers.averageOrNull()
        val medianPower = sortedPowers.percentile(0.50)
        val tenthPercentile = sortedPowers.percentile(0.10)
        val sourceCeiling = samples.mapNotNull { it.sourceCeilingWatts }.averageOrNull()
        val peakTemperature = samples.mapNotNull { it.temperatureCelsius }.maxOrNull()
        val thermalLimitedSeconds = samples.count {
            it.thermalStatus.severity >= ThermalStatus.MODERATE.severity
        }
        val endTimestamp = session.endedAtMillis
            ?: samples.lastOrNull()?.timestampMillis
            ?: session.events.lastOrNull()?.timestampMillis
            ?: session.startedAtMillis

        val summaryWithoutDiagnosis = ChargingSessionSummary(
            id = session.id,
            startedAtMillis = session.startedAtMillis,
            endedAtMillis = session.endedAtMillis,
            joinedInProgress = session.joinedInProgress,
            endReason = session.endReason,
            source = samples.firstOrNull()?.powerSource ?: session.initialSource,
            durationMillis = (endTimestamp - session.startedAtMillis).coerceAtLeast(0L),
            sampleCount = samples.size,
            startLevelPercent = samples.firstNotNullOfOrNull { it.levelPercent },
            endLevelPercent = samples.asReversed().firstNotNullOfOrNull { it.levelPercent },
            averagePowerWatts = averagePower,
            medianPowerWatts = medianPower,
            tenthPercentilePowerWatts = tenthPercentile,
            minimumPowerWatts = samples.mapNotNull { it.powerMinimumWatts }.minOrNull(),
            maximumPowerWatts = samples.mapNotNull { it.powerMaximumWatts }.maxOrNull(),
            peakTemperatureCelsius = peakTemperature,
            thermalLimitedSeconds = thermalLimitedSeconds,
            chargingProfile = samples
                .asReversed()
                .firstNotNullOfOrNull { it.chargingProfile },
            sourceCeilingWatts = sourceCeiling,
            diagnosis = SessionDiagnosis("", "", DiagnosisTone.NOTICE),
        )
        return summaryWithoutDiagnosis.copy(
            diagnosis = diagnose(summaryWithoutDiagnosis, session),
        )
    }

    private fun diagnose(
        summary: ChargingSessionSummary,
        session: ChargingSession,
    ): SessionDiagnosis {
        if (summary.sampleCount < 5) {
            return SessionDiagnosis(
                title = if (summary.isActive) "Collecting evidence" else "Not enough data",
                detail = "Keep the dashboard open while connected to capture a useful trace.",
                tone = DiagnosisTone.NOTICE,
            )
        }

        val averagePower = summary.averagePowerWatts
        if (averagePower != null && averagePower < -0.25) {
            return SessionDiagnosis(
                title = "Battery was still draining",
                detail = "Phone demand exceeded incoming power during this trace.",
                tone = DiagnosisTone.WARNING,
            )
        }

        if (summary.thermalLimitedSeconds > 0) {
            return SessionDiagnosis(
                title = "Heat likely limited charging",
                detail = "Android reported moderate or stronger thermal throttling during this trace.",
                tone = DiagnosisTone.WARNING,
            )
        }

        if (summary.chargingProfile == ChargingProfile.LONG_LIFE &&
            (summary.endLevelPercent ?: 0) >= 78
        ) {
            return SessionDiagnosis(
                title = "Charge limit was active",
                detail = "The long-life profile was holding the battery near its configured limit.",
                tone = DiagnosisTone.GOOD,
            )
        }

        if ((summary.peakTemperatureCelsius ?: 0.0) >= 40.0) {
            return SessionDiagnosis(
                title = "Warm battery may have reduced power",
                detail = "The battery reached 40 °C or higher, although Android did not report thermal throttling.",
                tone = DiagnosisTone.NOTICE,
            )
        }

        if ((summary.startLevelPercent ?: 0) >= 80 &&
            averagePower != null &&
            averagePower < 8.0
        ) {
            return SessionDiagnosis(
                title = "Likely normal high-level taper",
                detail = "Charging power normally falls as the battery approaches full.",
                tone = DiagnosisTone.GOOD,
            )
        }

        val sourceChanged = session.events.any {
            it.type == SessionEventType.SOURCE_CHANGED
        }
        val median = summary.medianPowerWatts
        val lowBuckets = if (median != null && median > 1.0) {
            session.samples.count {
                val power = it.powerAverageWatts
                power != null && power < median * 0.35
            }
        } else {
            0
        }
        val unstable = sourceChanged ||
            (session.samples.size >= 10 && lowBuckets >= ceil(session.samples.size * 0.20).toInt())
        if (unstable) {
            return SessionDiagnosis(
                title = "Power delivery was unstable",
                detail = "Repeated power drops or source changes suggest alignment, cable, or connection issues.",
                tone = DiagnosisTone.WARNING,
            )
        }

        val sourceCeiling = summary.sourceCeilingWatts
        if (averagePower != null &&
            averagePower < 5.0 &&
            sourceCeiling != null &&
            sourceCeiling <= 10.0
        ) {
            return SessionDiagnosis(
                title = "Source capability appears low",
                detail = "The power ceiling reported by Android points to the charger, pad, or cable.",
                tone = DiagnosisTone.WARNING,
            )
        }

        if (averagePower != null &&
            averagePower < 5.0 &&
            sourceCeiling != null &&
            sourceCeiling > 10.0
        ) {
            return SessionDiagnosis(
                title = "Low net battery power",
                detail = "The source ceiling was higher; phone load or hidden charger negotiation may explain the gap.",
                tone = DiagnosisTone.NOTICE,
            )
        }

        if (averagePower != null && averagePower < 5.0) {
            return SessionDiagnosis(
                title = "Low net battery power",
                detail = "Android did not expose enough source data to isolate charger capability from phone load.",
                tone = DiagnosisTone.NOTICE,
            )
        }

        return SessionDiagnosis(
            title = "Charging looks stable",
            detail = "No clear thermal, profile, source, or connection limit was detected.",
            tone = DiagnosisTone.GOOD,
        )
    }

    private fun List<Double>.percentile(fraction: Double): Double? {
        if (isEmpty()) return null
        val index = ((size - 1) * fraction).toInt().coerceIn(indices)
        return this[index]
    }

    private fun List<Double>.averageOrNull(): Double? =
        if (isEmpty()) null else average()
}
