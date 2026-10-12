package com.nicky.chargemonitor.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nicky.chargemonitor.R
import com.nicky.chargemonitor.session.ChargingSession
import com.nicky.chargemonitor.session.DiagnosticSample
import com.nicky.chargemonitor.session.SessionComparison
import com.nicky.chargemonitor.ui.components.ChartAxis
import com.nicky.chargemonitor.ui.components.ChartPoint
import com.nicky.chargemonitor.ui.components.ChartSeries
import com.nicky.chargemonitor.ui.components.EmptyState
import com.nicky.chargemonitor.ui.components.FootNote
import com.nicky.chargemonitor.ui.components.Pill
import com.nicky.chargemonitor.ui.components.ScreenScaffold
import com.nicky.chargemonitor.ui.components.SectionCard
import com.nicky.chargemonitor.ui.components.SectionLabel
import com.nicky.chargemonitor.ui.components.TraceChart
import com.nicky.chargemonitor.ui.components.downsample
import com.nicky.chargemonitor.ui.components.formatDuration
import com.nicky.chargemonitor.ui.components.formatSessionTime
import com.nicky.chargemonitor.ui.components.formatUnit
import com.nicky.chargemonitor.ui.components.levelRange
import com.nicky.chargemonitor.ui.components.screenContent
import com.nicky.chargemonitor.ui.components.shortLabel
import com.nicky.chargemonitor.ui.theme.ChargeTheme
import kotlin.math.max

@Composable
fun ComparisonScreen(
    sessions: List<ChargingSession>,
    comparison: SessionComparison?,
    loading: Boolean,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = "Comparison", onBack = onBack) { padding ->
        when {
            comparison != null && sessions.size == 2 -> {
                val names = remember(sessions) { sessions.map { it.displayName() } }
                LazyColumn(
                    contentPadding = padding.screenContent(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "result") { ResultCard(comparison, names[0], names[1]) }
                    item(key = "table") { MetricsTable(comparison) }
                    item(key = "power") {
                        OverlayChart(
                            title = "Power",
                            axis = ChartAxis(unit = "W", includeZero = true),
                            first = comparison.firstComparableTrace,
                            second = comparison.secondComparableTrace,
                            value = { it.powerAverageWatts },
                        )
                    }
                    item(key = "temperature") {
                        OverlayChart(
                            title = "Temperature",
                            axis = ChartAxis(unit = "°C", minimumSpan = 5.0),
                            first = comparison.firstComparableTrace,
                            second = comparison.secondComparableTrace,
                            value = { it.temperatureCelsius },
                        )
                    }
                    item(key = "quality") { QualityCard(comparison) }
                    item(key = "note") {
                        FootNote(
                            "Only the comparable part of each trace is used: the first 30 s are " +
                                "skipped, throttled samples are excluded, and both are matched to " +
                                "their shared battery range where possible.",
                        )
                    }
                }
            }

            loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(top = 64.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            else -> EmptyState(
                icon = R.drawable.ic_compare,
                title = "Nothing to compare",
                body = "One of these sessions is no longer stored. Pick two sessions from the list.",
                modifier = Modifier.padding(padding),
            )
        }
    }
}

private fun ChargingSession.displayName(): String =
    accessoryName ?: "${initialSource.shortLabel} · ${startedAtMillis.formatSessionTime()}"

@Composable
private fun ResultCard(comparison: SessionComparison, firstName: String, secondName: String) {
    val colors = ChargeTheme.colors
    val confidenceColor = when {
        comparison.confidenceScore >= 8 -> colors.good
        comparison.confidenceScore >= 5 -> colors.notice
        else -> colors.warning
    }
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                comparison.headline,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.width(12.dp))
            Pill("${comparison.confidenceScore}/10 confidence", color = confidenceColor)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            comparison.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        NameRow("A", firstName, colors.seriesA)
        Spacer(Modifier.height(8.dp))
        NameRow("B", secondName, colors.seriesB)
    }
}

@Composable
private fun NameRow(letter: String, name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.surface)
        }
        Spacer(Modifier.width(10.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MetricsTable(comparison: SessionComparison) {
    val colors = ChargeTheme.colors
    val a = comparison.first
    val b = comparison.second
    SectionCard {
        Row(Modifier.padding(bottom = 6.dp)) {
            Spacer(Modifier.weight(1.3f))
            Text("A", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = colors.seriesA, textAlign = TextAlign.End)
            Text("B", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = colors.seriesB, textAlign = TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TableRow("Average", a.averagePowerWatts.formatUnit(2, "W"), b.averagePowerWatts.formatUnit(2, "W"), emphasize = true)
        TableRow("Low 10%", a.tenthPercentilePowerWatts.formatUnit(2, "W"), b.tenthPercentilePowerWatts.formatUnit(2, "W"))
        TableRow("Peak", a.peakPowerWatts.formatUnit(1, "W"), b.peakPowerWatts.formatUnit(1, "W"))
        TableRow("Max temp", a.peakTemperatureCelsius.formatUnit(1, "°C"), b.peakTemperatureCelsius.formatUnit(1, "°C"))
        TableRow(
            "Battery",
            levelRange(a.startLevelPercent, a.endLevelPercent),
            levelRange(b.startLevelPercent, b.endLevelPercent),
        )
        TableRow(
            "Data used",
            (a.bucketCount * 1_000L).formatDuration(),
            (b.bucketCount * 1_000L).formatDuration(),
        )
    }
}

@Composable
private fun TableRow(label: String, first: String, second: String, emphasize: Boolean = false) {
    val style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1.3f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(first, Modifier.weight(1f), style = style, textAlign = TextAlign.End, maxLines = 1)
        Text(second, Modifier.weight(1f), style = style, textAlign = TextAlign.End, maxLines = 1)
    }
}

/** Both traces are aligned to their own start so A and B overlay from 0:00. */
@Composable
private fun OverlayChart(
    title: String,
    axis: ChartAxis,
    first: List<DiagnosticSample>,
    second: List<DiagnosticSample>,
    value: (DiagnosticSample) -> Double?,
) {
    val colors = ChargeTheme.colors
    val series = remember(first, second, colors) {
        listOf(first to colors.seriesA, second to colors.seriesB).map { (samples, color) ->
            val origin = samples.firstOrNull()?.timestampMillis ?: 0L
            ChartSeries(
                points = samples.downsample(MAX_OVERLAY_POINTS).mapNotNull { s ->
                    value(s)?.let { ChartPoint(s.timestampMillis - origin, it) }
                },
                color = color,
            )
        }
    }
    val length = max(1L, series.maxOf { s -> s.points.lastOrNull()?.x ?: 0L })
    SectionCard {
        SectionLabel(title)
        Spacer(Modifier.height(12.dp))
        TraceChart(series = series, xStart = 0L, xEnd = length, primaryAxis = axis, height = 150.dp)
    }
}

@Composable
private fun QualityCard(comparison: SessionComparison) {
    SectionCard {
        SectionLabel("How fair is this comparison?")
        Spacer(Modifier.height(6.dp))
        if (comparison.confidenceNotes.isEmpty()) {
            Text(
                "Conditions matched well: similar battery level, temperature, connection type, " +
                    "and enough data on both sides.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            comparison.confidenceNotes.forEach { note ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(
                        "•",
                        modifier = Modifier.width(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChargeTheme.colors.warning,
                    )
                    Text(
                        note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val MAX_OVERLAY_POINTS = 300
