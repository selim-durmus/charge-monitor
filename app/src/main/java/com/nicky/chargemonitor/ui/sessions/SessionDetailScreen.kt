package com.nicky.chargemonitor.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nicky.chargemonitor.R
import com.nicky.chargemonitor.session.ChargingSession
import com.nicky.chargemonitor.session.ChargingSessionSummary
import com.nicky.chargemonitor.session.SessionAnalyzer
import com.nicky.chargemonitor.session.SessionEvent
import com.nicky.chargemonitor.session.SessionExportFormat
import com.nicky.chargemonitor.ui.components.AppIcon
import com.nicky.chargemonitor.ui.components.ChartAxis
import com.nicky.chargemonitor.ui.components.ChartBand
import com.nicky.chargemonitor.ui.components.ChartLegend
import com.nicky.chargemonitor.ui.components.ChartPoint
import com.nicky.chargemonitor.ui.components.ChartSeries
import com.nicky.chargemonitor.ui.components.FootNote
import com.nicky.chargemonitor.ui.components.KeyValueRow
import com.nicky.chargemonitor.ui.components.Pill
import com.nicky.chargemonitor.ui.components.ScreenScaffold
import com.nicky.chargemonitor.ui.components.SectionCard
import com.nicky.chargemonitor.ui.components.SectionLabel
import com.nicky.chargemonitor.ui.components.StatTile
import com.nicky.chargemonitor.ui.components.TraceChart
import com.nicky.chargemonitor.ui.components.color
import com.nicky.chargemonitor.ui.components.downsample
import com.nicky.chargemonitor.ui.components.formatClock
import com.nicky.chargemonitor.ui.components.formatDuration
import com.nicky.chargemonitor.ui.components.formatSessionTime
import com.nicky.chargemonitor.ui.components.formatUnit
import com.nicky.chargemonitor.ui.components.levelRange
import com.nicky.chargemonitor.ui.components.screenContent
import com.nicky.chargemonitor.ui.theme.ChargeTheme

@Composable
fun SessionDetailScreen(
    session: ChargingSession?,
    summary: ChargingSessionSummary?,
    isRecording: Boolean,
    onBack: () -> Unit,
    onShare: (SessionExportFormat) -> Unit,
) {
    var shareMenuOpen by remember { mutableStateOf(false) }
    ScreenScaffold(
        title = "Session report",
        subtitle = session?.startedAtMillis?.formatSessionTime(),
        onBack = onBack,
        actions = {
            if (session != null) {
                Box {
                    IconButton(onClick = { shareMenuOpen = true }) {
                        AppIcon(R.drawable.ic_share, contentDescription = "Share session")
                    }
                    DropdownMenu(expanded = shareMenuOpen, onDismissRequest = { shareMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Share as CSV") },
                            onClick = {
                                shareMenuOpen = false
                                onShare(SessionExportFormat.CSV)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share as JSON") },
                            onClick = {
                                shareMenuOpen = false
                                onShare(SessionExportFormat.JSON)
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (session == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(top = 64.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val report = remember(session, summary) { summary ?: SessionAnalyzer.summarize(session) }
            LazyColumn(
                contentPadding = padding.screenContent(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "diagnosis") { DiagnosisCard(report, isRecording) }
                item(key = "stats") { KeyStats(report) }
                item(key = "trace") { TraceCard(session) }
                item(key = "details") { DetailsCard(report) }
                item(key = "events") { EventsCard(session.events, session.startedAtMillis) }
                item(key = "note") {
                    FootNote(
                        "Reports use Android's battery-side net current. They reveal patterns but " +
                            "not every USB-PD, PPS, or Qi negotiation detail.",
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosisCard(summary: ChargingSessionSummary, isRecording: Boolean) {
    val tone = summary.diagnosis.tone.color()
    SectionCard(color = tone.copy(alpha = 0.12f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Likely cause",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = tone,
            )
            if (isRecording) {
                Pill("Recording", color = MaterialTheme.colorScheme.error, showDot = true)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(summary.diagnosis.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            summary.diagnosis.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summary.joinedInProgress) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Recording began after charging had already started.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KeyStats(summary: ChargingSessionSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                "Average power",
                summary.averagePowerWatts.formatUnit(1, "W"),
                Modifier.weight(1f),
                accent = ChargeTheme.colors.power,
            )
            StatTile(
                "Low 10%",
                summary.tenthPercentilePowerWatts.formatUnit(1, "W"),
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                "Battery",
                levelRange(summary.startLevelPercent, summary.endLevelPercent),
                Modifier.weight(1f),
            )
            StatTile("Duration", summary.durationMillis.formatDuration(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Peak power", summary.maximumPowerWatts.formatUnit(1, "W"), Modifier.weight(1f))
            StatTile(
                "Max temp",
                summary.peakTemperatureCelsius.formatUnit(1, "°C"),
                Modifier.weight(1f),
                accent = ChargeTheme.colors.temperature,
            )
        }
    }
}

@Composable
private fun TraceCard(session: ChargingSession) {
    val colors = ChargeTheme.colors
    val samples = remember(session.samples) { session.samples.downsample(MAX_TRACE_POINTS) }
    val series = remember(samples, colors) {
        listOf(
            ChartSeries(
                points = samples.mapNotNull { s -> s.powerAverageWatts?.let { ChartPoint(s.timestampMillis, it) } },
                color = colors.power,
                band = samples.mapNotNull { s ->
                    val low = s.powerMinimumWatts
                    val high = s.powerMaximumWatts
                    if (low != null && high != null) ChartBand(s.timestampMillis, low, high) else null
                },
                strokeWidth = 2.5.dp,
            ),
            ChartSeries(
                points = samples.mapNotNull { s -> s.temperatureCelsius?.let { ChartPoint(s.timestampMillis, it) } },
                color = colors.temperature,
                onSecondaryAxis = true,
                strokeWidth = 1.5.dp,
            ),
        )
    }
    val end = session.endedAtMillis ?: session.samples.lastOrNull()?.timestampMillis ?: session.startedAtMillis
    SectionCard {
        SectionLabel("Power & temperature")
        Spacer(Modifier.height(4.dp))
        ChartLegend(
            listOf(
                colors.power to "Power (W)",
                colors.temperature to "Temp (°C)",
                colors.event to "Event",
            ),
        )
        Spacer(Modifier.height(12.dp))
        if (session.samples.size < 2) {
            Text(
                "Not enough samples to draw a trace yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            TraceChart(
                series = series,
                xStart = session.startedAtMillis,
                xEnd = end,
                primaryAxis = ChartAxis(unit = "W", includeZero = true),
                secondaryAxis = ChartAxis(unit = "°C", minimumSpan = 5.0),
                eventMarkers = session.events.map { it.timestampMillis },
            )
        }
    }
}

@Composable
private fun DetailsCard(summary: ChargingSessionSummary) {
    SectionCard {
        SectionLabel("Details")
        Spacer(Modifier.height(4.dp))
        KeyValueRow("Charger", summary.accessoryName ?: "Unnamed")
        KeyValueRow("Connection", summary.source.label)
        KeyValueRow("Charging profile", summary.chargingProfile?.label ?: "System managed")
        KeyValueRow("Charger rating", summary.sourceCeilingWatts?.formatUnit(1, "W") ?: "Not reported")
        KeyValueRow(
            "Thermal throttling",
            if (summary.thermalLimitedSeconds == 0) {
                "None"
            } else {
                (summary.thermalLimitedSeconds * 1_000L).formatDuration()
            },
            valueColor = if (summary.thermalLimitedSeconds > 0) {
                ChargeTheme.colors.warning
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        KeyValueRow(
            "Peak thermal forecast",
            summary.peakThermalHeadroomForecast?.let { "${(it * 100).toInt()}% of severe limit" } ?: "Unavailable",
        )
        KeyValueRow("Samples", "${summary.sampleCount} × 1 s")
    }
}

@Composable
private fun EventsCard(events: List<SessionEvent>, startedAtMillis: Long) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val visible = if (showAll) events else events.take(COLLAPSED_EVENT_COUNT)
    SectionCard {
        SectionLabel("Events")
        Spacer(Modifier.height(4.dp))
        if (events.isEmpty()) {
            Text(
                "No state changes were recorded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        visible.forEach { event ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = (event.timestampMillis - startedAtMillis).formatClock(),
                    modifier = Modifier.width(64.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = ChargeTheme.colors.event,
                )
                Text(event.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (events.size > COLLAPSED_EVENT_COUNT) {
            TextButton(onClick = { showAll = !showAll }, modifier = Modifier.align(Alignment.End)) {
                Text(if (showAll) "Show fewer" else "Show all ${events.size}")
            }
        }
    }
}

private const val MAX_TRACE_POINTS = 360
private const val COLLAPSED_EVENT_COUNT = 5
