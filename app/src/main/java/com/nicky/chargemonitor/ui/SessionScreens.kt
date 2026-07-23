package com.nicky.chargemonitor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nicky.chargemonitor.DashboardState
import com.nicky.chargemonitor.session.ChargingSession
import com.nicky.chargemonitor.session.ChargingSessionSummary
import com.nicky.chargemonitor.session.DiagnosisTone
import com.nicky.chargemonitor.session.SessionAnalyzer
import com.nicky.chargemonitor.session.SessionEvent
import com.nicky.chargemonitor.session.SessionExportFormat
import com.nicky.chargemonitor.ui.theme.AppBackground
import com.nicky.chargemonitor.ui.theme.CardBackground
import com.nicky.chargemonitor.ui.theme.CardBackgroundElevated
import com.nicky.chargemonitor.ui.theme.DividerGreen
import com.nicky.chargemonitor.ui.theme.EnergyCyan
import com.nicky.chargemonitor.ui.theme.EnergyGreen
import com.nicky.chargemonitor.ui.theme.EnergyGreenMuted
import com.nicky.chargemonitor.ui.theme.TextPrimary
import com.nicky.chargemonitor.ui.theme.TextSecondary
import com.nicky.chargemonitor.ui.theme.WarningAmber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private enum class AppDestination {
    DASHBOARD,
    SESSIONS,
    DETAIL,
}

@Composable
fun ChargeMonitorApp(
    state: DashboardState,
    onSelectSession: (String) -> Unit,
    onClearSelectedSession: () -> Unit,
    onShareSession: (String, SessionExportFormat) -> Unit,
) {
    var destinationName by rememberSaveable {
        mutableStateOf(AppDestination.DASHBOARD.name)
    }
    val destination = AppDestination.entries.firstOrNull { it.name == destinationName }
        ?: AppDestination.DASHBOARD

    BackHandler(enabled = destination != AppDestination.DASHBOARD) {
        if (destination == AppDestination.DETAIL) {
            onClearSelectedSession()
            destinationName = AppDestination.SESSIONS.name
        } else {
            destinationName = AppDestination.DASHBOARD.name
        }
    }

    when (destination) {
        AppDestination.DASHBOARD -> ChargeDashboard(
            state = state,
            onOpenSessions = { destinationName = AppDestination.SESSIONS.name },
        )

        AppDestination.SESSIONS -> SessionHistoryScreen(
            summaries = state.sessionSummaries,
            activeSessionId = state.activeSessionId,
            onBack = { destinationName = AppDestination.DASHBOARD.name },
            onOpenSession = { sessionId ->
                onSelectSession(sessionId)
                destinationName = AppDestination.DETAIL.name
            },
        )

        AppDestination.DETAIL -> SessionDetailScreen(
            session = state.selectedSession,
            fallbackSummary = state.sessionSummaries.firstOrNull {
                it.id == state.selectedSession?.id
            },
            onBack = {
                onClearSelectedSession()
                destinationName = AppDestination.SESSIONS.name
            },
            onShareSession = onShareSession,
        )
    }
}

@Composable
private fun SessionHistoryScreen(
    summaries: List<ChargingSessionSummary>,
    activeSessionId: String?,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item {
                ScreenHeader(
                    eyebrow = "DIAGNOSTICS",
                    title = "Charging sessions",
                    onBack = onBack,
                )
            }
            item {
                RetentionNote(
                    active = activeSessionId != null,
                    count = summaries.size,
                )
            }
            if (summaries.isEmpty()) {
                item { EmptySessionsCard() }
            } else {
                summaries.forEach { summary ->
                    item(key = summary.id) {
                        SessionSummaryCard(
                            summary = summary,
                            isRecording = summary.id == activeSessionId,
                            onClick = { onOpenSession(summary.id) },
                        )
                    }
                }
            }
            item { DiagnosticLimitNote() }
        }
    }
}

@Composable
private fun SessionDetailScreen(
    session: ChargingSession?,
    fallbackSummary: ChargingSessionSummary?,
    onBack: () -> Unit,
    onShareSession: (String, SessionExportFormat) -> Unit,
) {
    val reportSummary = remember(session) {
        session?.let(SessionAnalyzer::summarize)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ScreenHeader(
                    eyebrow = "SESSION REPORT",
                    title = session?.startedAtMillis?.formatSessionTitle() ?: "Loading trace…",
                    onBack = onBack,
                )
            }
            if (session == null) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = CardBackground,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Preparing diagnostic report…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            } else {
                val summary = reportSummary ?: SessionAnalyzer.summarize(session)
                item { DiagnosisHero(summary = summary) }
                item { SessionStats(summary = summary) }
                item {
                    SessionTraceCard(
                        session = session,
                        summary = fallbackSummary ?: summary,
                    )
                }
                item {
                    EventTimeline(
                        events = session.events,
                        startedAtMillis = session.startedAtMillis,
                    )
                }
                item {
                    ExportCard(
                        onJson = { onShareSession(session.id, SessionExportFormat.JSON) },
                        onCsv = { onShareSession(session.id, SessionExportFormat.CSV) },
                    )
                }
                item { DiagnosticLimitNote() }
            }
        }
    }
}

@Composable
private fun ScreenHeader(
    eyebrow: String,
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .border(1.dp, DividerGreen, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
            )
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = EnergyGreen,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
        }
    }
}

@Composable
private fun RetentionNote(active: Boolean, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EnergyGreenMuted.copy(alpha = 0.44f), RoundedCornerShape(18.dp))
            .border(1.dp, EnergyGreen.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(if (active) EnergyGreen else TextSecondary, CircleShape),
            )
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    text = if (active) "RECORDING NOW" else "FOREGROUND RECORDER",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) EnergyGreen else TextSecondary,
                )
                Text(
                    text = "One-second diagnostic buckets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
        }
        Text(
            text = "$count / 5",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
    }
}

@Composable
private fun EmptySessionsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(24.dp))
            .border(1.dp, DividerGreen, RoundedCornerShape(24.dp))
            .padding(horizontal = 22.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No sessions yet",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = "Connect a charger while this app is open. Recording starts automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SessionSummaryCard(
    summary: ChargingSessionSummary,
    isRecording: Boolean,
    onClick: () -> Unit,
) {
    val accent = summary.diagnosis.tone.color()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(23.dp))
            .border(
                1.dp,
                if (isRecording) EnergyGreen.copy(alpha = 0.5f) else DividerGreen,
                RoundedCornerShape(23.dp),
            )
            .clickable(onClick = onClick)
            .padding(17.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = summary.startedAtMillis.formatDayAndTime(),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Text(
                    text = "${summary.source.label} · ${summary.durationMillis.formatDuration()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Text(
                text = if (isRecording) "REC" else summary.levelRange(),
                modifier = Modifier
                    .background(
                        if (isRecording) EnergyGreen.copy(alpha = 0.14f)
                        else CardBackgroundElevated,
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (isRecording) EnergyGreen else TextPrimary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactStat(
                label = "AVERAGE",
                value = summary.averagePowerWatts.formatMetric("W", 1),
                modifier = Modifier.weight(1f),
            )
            CompactStat(
                label = "LOW 10%",
                value = summary.tenthPercentilePowerWatts.formatMetric("W", 1),
                modifier = Modifier.weight(1f),
            )
            CompactStat(
                label = "PEAK TEMP",
                value = summary.peakTemperatureCelsius.formatMetric("°C", 1),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(accent, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.diagnosis.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                )
                Text(
                    text = summary.diagnosis.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(text = "›", style = MaterialTheme.typography.titleLarge, color = TextSecondary)
        }
    }
}

@Composable
private fun DiagnosisHero(summary: ChargingSessionSummary) {
    val accent = summary.diagnosis.tone.color()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = when (summary.diagnosis.tone) {
                    DiagnosisTone.GOOD -> Color(0xFF0E2B20)
                    DiagnosisTone.NOTICE -> Color(0xFF10272D)
                    DiagnosisTone.WARNING -> Color(0xFF2D2314)
                },
                shape = RoundedCornerShape(27.dp),
            )
            .border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(27.dp))
            .padding(20.dp),
    ) {
        Text(
            text = "LIKELY CAUSE",
            style = MaterialTheme.typography.labelMedium,
            color = accent,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = summary.diagnosis.title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = summary.diagnosis.detail,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        if (summary.joinedInProgress) {
            Spacer(Modifier.height(13.dp))
            Text(
                text = "This trace began after charging had already started.",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun SessionStats(summary: ChargingSessionSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = "SESSION SUMMARY",
            modifier = Modifier.padding(start = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ReportStat(
                "Average power",
                summary.averagePowerWatts.formatMetric("W", 2),
                EnergyGreen,
                Modifier.weight(1f),
            )
            ReportStat(
                "Power range",
                "${summary.minimumPowerWatts.formatBare(1)}–" +
                    "${summary.maximumPowerWatts.formatBare(1)} W",
                EnergyCyan,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ReportStat(
                "Battery",
                summary.levelRange(),
                WarningAmber,
                Modifier.weight(1f),
            )
            ReportStat(
                "Duration",
                summary.durationMillis.formatDuration(),
                Color(0xFFFF8FA3),
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ReportStat(
                "Profile",
                summary.chargingProfile?.label ?: "System managed",
                EnergyCyan,
                Modifier.weight(1f),
            )
            ReportStat(
                "Source ceiling",
                summary.sourceCeilingWatts.formatMetric("W", 1),
                EnergyGreen,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReportStat(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .height(105.dp)
            .background(CardBackground, RoundedCornerShape(20.dp))
            .border(1.dp, DividerGreen, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
    }
}

@Composable
private fun CompactStat(
    label: String,
    value: String,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .background(CardBackgroundElevated, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(Modifier.height(3.dp))
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
    }
}

@Composable
private fun SessionTraceCard(
    session: ChargingSession,
    summary: ChargingSessionSummary,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(24.dp))
            .border(1.dp, DividerGreen, RoundedCornerShape(24.dp))
            .padding(17.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = "POWER + TEMPERATURE",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
                Text(
                    text = "${session.samples.size} one-second buckets",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = summary.peakTemperatureCelsius.formatMetric("°C", 1),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFFF8FA3),
                )
                Text(
                    text = "PEAK TEMP",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SessionTrace(
            session = session,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
        )
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ChartLegend(color = EnergyGreen, label = "Average watts")
            ChartLegend(color = Color(0xFFFF8FA3), label = "Temperature")
            ChartLegend(color = WarningAmber, label = "Event")
        }
    }
}

@Composable
private fun SessionTrace(
    session: ChargingSession,
    modifier: Modifier = Modifier,
) {
    val samples = remember(session.samples) { session.samples.downsample(420) }
    Canvas(modifier = modifier) {
        repeat(5) { row ->
            val y = size.height * row / 4f
            drawLine(
                color = DividerGreen.copy(alpha = 0.62f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        if (samples.size < 2) {
            drawLine(
                color = EnergyGreen.copy(alpha = 0.3f),
                start = Offset.Zero.copy(y = size.height * 0.7f),
                end = Offset(size.width, size.height * 0.7f),
                strokeWidth = 2.dp.toPx(),
            )
            return@Canvas
        }

        val totalMillis = max(
            1L,
            (session.endedAtMillis ?: samples.last().timestampMillis) - session.startedAtMillis,
        )
        val powerValues = samples.flatMap {
            listOfNotNull(it.powerMinimumWatts, it.powerAverageWatts, it.powerMaximumWatts)
        }
        val rawPowerMin = min(0.0, powerValues.minOrNull() ?: 0.0)
        val rawPowerMax = max(1.0, powerValues.maxOrNull() ?: 1.0)
        val powerPadding = max(0.5, (rawPowerMax - rawPowerMin) * 0.1)
        val powerMin = rawPowerMin - powerPadding
        val powerMax = rawPowerMax + powerPadding
        val powerRange = powerMax - powerMin
        val temperatureValues = samples.mapNotNull { it.temperatureCelsius }
        val temperatureMin = min(20.0, temperatureValues.minOrNull() ?: 20.0)
        val temperatureMax = max(45.0, temperatureValues.maxOrNull() ?: 45.0)
        val temperatureRange = temperatureMax - temperatureMin

        fun xFor(timestampMillis: Long): Float =
            (size.width * ((timestampMillis - session.startedAtMillis).toDouble() / totalMillis))
                .toFloat()
                .coerceIn(0f, size.width)

        fun powerY(value: Double): Float =
            (size.height * (1.0 - (value - powerMin) / powerRange)).toFloat()

        fun temperatureY(value: Double): Float =
            (size.height * (1.0 - (value - temperatureMin) / temperatureRange)).toFloat()

        session.events.forEach { event ->
            val x = xFor(event.timestampMillis)
            drawLine(
                color = WarningAmber.copy(alpha = 0.48f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }

        samples.forEach { sample ->
            val low = sample.powerMinimumWatts
            val high = sample.powerMaximumWatts
            if (low != null && high != null) {
                val x = xFor(sample.timestampMillis)
                drawLine(
                    color = EnergyGreen.copy(alpha = 0.18f),
                    start = Offset(x, powerY(low)),
                    end = Offset(x, powerY(high)),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        val powerPath = Path()
        var powerStarted = false
        samples.forEach { sample ->
            sample.powerAverageWatts?.let { power ->
                val x = xFor(sample.timestampMillis)
                val y = powerY(power)
                if (!powerStarted) {
                    powerPath.moveTo(x, y)
                    powerStarted = true
                } else {
                    powerPath.lineTo(x, y)
                }
            }
        }
        if (powerStarted) {
            drawPath(
                path = powerPath,
                color = EnergyGreen,
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
        }

        val temperaturePath = Path()
        var temperatureStarted = false
        samples.forEach { sample ->
            sample.temperatureCelsius?.let { temperature ->
                val x = xFor(sample.timestampMillis)
                val y = temperatureY(temperature)
                if (!temperatureStarted) {
                    temperaturePath.moveTo(x, y)
                    temperatureStarted = true
                } else {
                    temperaturePath.lineTo(x, y)
                }
            }
        }
        if (temperatureStarted) {
            drawPath(
                path = temperaturePath,
                color = Color(0xFFFF8FA3),
                style = Stroke(
                    width = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
        }
    }
}

@Composable
private fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(5.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun EventTimeline(
    events: List<SessionEvent>,
    startedAtMillis: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "EVENTS",
            modifier = Modifier.padding(start = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
        if (events.isEmpty()) {
            Text(
                text = "No state changes were recorded.",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(18.dp))
                    .padding(15.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        } else {
            events.forEach { event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackground, RoundedCornerShape(16.dp))
                        .border(1.dp, DividerGreen, RoundedCornerShape(16.dp))
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (event.timestampMillis - startedAtMillis)
                            .coerceAtLeast(0L)
                            .formatOffset(),
                        modifier = Modifier.width(58.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = WarningAmber,
                    )
                    Text(
                        text = event.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportCard(
    onJson: () -> Unit,
    onCsv: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(22.dp))
            .border(1.dp, DividerGreen, RoundedCornerShape(22.dp))
            .padding(17.dp),
    ) {
        Text(
            text = "EXPORT TRACE",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Share the full report for deeper analysis.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ExportButton(
                label = "SHARE JSON",
                filled = true,
                onClick = onJson,
                modifier = Modifier.weight(1f),
            )
            ExportButton(
                label = "SHARE CSV",
                filled = false,
                onClick = onCsv,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ExportButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .height(45.dp)
            .background(
                if (filled) EnergyGreen else Color.Transparent,
                RoundedCornerShape(14.dp),
            )
            .border(
                1.dp,
                if (filled) EnergyGreen else EnergyGreen.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) AppBackground else EnergyGreen,
        )
    }
}

@Composable
private fun DiagnosticLimitNote() {
    Text(
        text = "Reports use Android's battery-side net current. They can identify patterns, " +
            "but not every USB-PD, PPS, or Qi negotiation detail.",
        modifier = Modifier
            .fillMaxWidth()
            .background(EnergyGreenMuted.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
    )
}

private fun DiagnosisTone.color(): Color = when (this) {
    DiagnosisTone.GOOD -> EnergyGreen
    DiagnosisTone.NOTICE -> EnergyCyan
    DiagnosisTone.WARNING -> WarningAmber
}

private fun ChargingSessionSummary.levelRange(): String {
    val start = startLevelPercent?.let { "$it%" } ?: "—"
    val end = endLevelPercent?.let { "$it%" } ?: "—"
    return "$start → $end"
}

private fun Double?.formatMetric(unit: String, decimals: Int): String =
    if (this == null || !isFinite()) {
        "—"
    } else {
        String.format(Locale.US, "%.${decimals}f %s", this, unit)
    }

private fun Double?.formatBare(decimals: Int): String =
    if (this == null || !isFinite()) {
        "—"
    } else {
        String.format(Locale.US, "%.${decimals}f", this)
    }

private fun Long.formatDuration(): String {
    val seconds = this / 1_000L
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val remainderSeconds = seconds % 60L
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${remainderSeconds}s"
        else -> "${remainderSeconds}s"
    }
}

private fun Long.formatOffset(): String {
    val totalSeconds = this / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun Long.formatDayAndTime(): String =
    SESSION_LIST_FORMAT.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

private fun Long.formatSessionTitle(): String =
    SESSION_TITLE_FORMAT.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

private fun <T> List<T>.downsample(maxPoints: Int): List<T> {
    if (size <= maxPoints) return this
    val stride = ceil(size.toDouble() / maxPoints).toInt()
    return filterIndexed { index, _ -> index % stride == 0 }.let { sampled ->
        if (sampled.lastOrNull() == lastOrNull()) sampled else sampled + last()
    }
}

private val SESSION_LIST_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")
private val SESSION_TITLE_FORMAT = DateTimeFormatter.ofPattern("MMM d · h:mm a")
