package com.nicky.chargemonitor.session

import java.util.Locale
import kotlin.math.abs

data class ComparisonMetrics(
    val averagePowerWatts: Double?,
    val tenthPercentilePowerWatts: Double?,
    val peakPowerWatts: Double?,
    val peakTemperatureCelsius: Double?,
    val startLevelPercent: Int?,
    val endLevelPercent: Int?,
    val bucketCount: Int,
)

data class SessionComparison(
    val first: ComparisonMetrics,
    val second: ComparisonMetrics,
    val firstComparableTrace: List<DiagnosticSample>,
    val secondComparableTrace: List<DiagnosticSample>,
    val averagePowerDeltaWatts: Double?,
    val confidenceScore: Int,
    /** Plain-language reasons the score was reduced; empty when conditions matched well. */
    val confidenceNotes: List<String>,
    val headline: String,
    val detail: String,
)

object SessionComparator {
    fun compare(
        firstSession: ChargingSession,
        secondSession: ChargingSession,
    ): SessionComparison {
        val commonLevelRange = commonBatteryRange(firstSession, secondSession)
        val firstSamples = firstSession.comparableSamples(commonLevelRange)
        val secondSamples = secondSession.comparableSamples(commonLevelRange)
        val first = firstSamples.metrics()
        val second = secondSamples.metrics()
        val delta = if (first.averagePowerWatts != null && second.averagePowerWatts != null) {
            first.averagePowerWatts - second.averagePowerWatts
        } else {
            null
        }
        val (confidence, confidenceNotes) = comparisonConfidence(
            firstSession = firstSession,
            secondSession = secondSession,
            first = first,
            second = second,
            commonLevelRange = commonLevelRange,
        )
        val firstName = firstSession.accessoryName ?: "Session A"
        val secondName = secondSession.accessoryName ?: "Session B"
        val headline = when {
            delta == null -> "Not enough comparable data"
            abs(delta) < 0.5 -> "Charging performance was similar"
            delta > 0.0 -> "$firstName delivered more net power"
            else -> "$secondName delivered more net power"
        }
        val detail = when {
            delta == null -> "Both traces need more valid one-second buckets."
            abs(delta) < 0.5 -> "Average battery-side power differed by less than 0.5 W."
            else -> "Average battery-side power differed by ${
                String.format(Locale.US, "%.1f", abs(delta))
            } W " +
                "across the most comparable portion of each trace."
        }
        return SessionComparison(
            first = first,
            second = second,
            firstComparableTrace = firstSamples,
            secondComparableTrace = secondSamples,
            averagePowerDeltaWatts = delta,
            confidenceScore = confidence,
            confidenceNotes = confidenceNotes,
            headline = headline,
            detail = detail,
        )
    }

    private fun commonBatteryRange(
        first: ChargingSession,
        second: ChargingSession,
    ): IntRange? {
        val firstLevels = first.samples.mapNotNull { it.levelPercent }
        val secondLevels = second.samples.mapNotNull { it.levelPercent }
        if (firstLevels.isEmpty() || secondLevels.isEmpty()) return null
        val low = maxOf(firstLevels.min(), secondLevels.min())
        val high = minOf(firstLevels.max(), secondLevels.max())
        return if (low <= high) low..high else null
    }

    private fun ChargingSession.comparableSamples(levelRange: IntRange?): List<DiagnosticSample> {
        val candidates = samples.filter { sample ->
            sample.elapsedMillis >= COMPARISON_WARMUP_MILLIS &&
                sample.powerAverageWatts != null &&
                sample.thermalStatus.severity < 2
        }
        if (levelRange == null) return candidates
        val inRange = candidates.filter { sample ->
            sample.levelPercent?.let(levelRange::contains) == true
        }
        return inRange.takeIf { it.size >= MINIMUM_COMPARISON_BUCKETS } ?: candidates
    }

    private fun List<DiagnosticSample>.metrics(): ComparisonMetrics {
        val powers = mapNotNull { it.powerAverageWatts }.sorted()
        return ComparisonMetrics(
            averagePowerWatts = powers.takeIf { it.isNotEmpty() }?.average(),
            tenthPercentilePowerWatts = powers.percentileOrNull(0.10),
            peakPowerWatts = mapNotNull { it.powerMaximumWatts }.maxOrNull(),
            peakTemperatureCelsius = mapNotNull { it.temperatureCelsius }.maxOrNull(),
            startLevelPercent = firstNotNullOfOrNull { it.levelPercent },
            endLevelPercent = asReversed().firstNotNullOfOrNull { it.levelPercent },
            bucketCount = size,
        )
    }

    private fun comparisonConfidence(
        firstSession: ChargingSession,
        secondSession: ChargingSession,
        first: ComparisonMetrics,
        second: ComparisonMetrics,
        commonLevelRange: IntRange?,
    ): Pair<Int, List<String>> {
        val notes = mutableListOf<String>()
        if (first.bucketCount < 60 || second.bucketCount < 60) {
            notes += "At least one trace has under a minute of comparable data."
        }
        if (firstSession.initialSource != secondSession.initialSource) {
            notes += "The sessions used different connection types."
        }
        if (commonLevelRange == null) {
            notes += "The battery-level ranges do not overlap."
        }
        val levelDifference = listOfNotNull(
            first.startLevelPercent,
            second.startLevelPercent,
        ).let { levels -> if (levels.size == 2) abs(levels[0] - levels[1]) else 10 }
        if (levelDifference > 5) {
            notes += "Starting battery levels differ by more than 5%."
        }
        val firstStartTemperature = firstSession.samples.firstNotNullOfOrNull {
            it.temperatureCelsius
        }
        val secondStartTemperature = secondSession.samples.firstNotNullOfOrNull {
            it.temperatureCelsius
        }
        if (firstStartTemperature != null &&
            secondStartTemperature != null &&
            abs(firstStartTemperature - secondStartTemperature) > 3.0
        ) {
            notes += "Starting temperatures differ by more than 3 °C."
        }
        return (10 - notes.size * 2).coerceIn(1, 10) to notes
    }

    private fun List<Double>.percentileOrNull(fraction: Double): Double? {
        if (isEmpty()) return null
        val index = ((size - 1) * fraction).toInt().coerceIn(indices)
        return this[index]
    }

    private const val COMPARISON_WARMUP_MILLIS = 30_000L
    private const val MINIMUM_COMPARISON_BUCKETS = 15
}
