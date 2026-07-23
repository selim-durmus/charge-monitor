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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.nicky.chargemonitor.session.SessionComparator
import com.nicky.chargemonitor.session.SessionEvent
import com.nicky.chargemonitor.session.SessionExportFormat
import com.nicky.chargemonitor.session.GuidedChargingTestState
import com.nicky.chargemonitor.session.GuidedTestPhase
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
import kotlinx.coroutines.delay

private enum class AppDestination {
    DASHBOARD,
    SESSIONS,
    DETAIL,
    COMPARISON,
    GUIDED_TEST,
}

@Composable
fun ChargeMonitorApp(
    state: DashboardState,
    onSelectSession: (String) -> Unit,
    onClearSelectedSession: () -> Unit,
    onShareSession: (String, SessionExportFormat) -> Unit,
    onNameAccessory: (String) -> Unit,
    onSelectComparison: (List<String>) -> Unit,
    onClearComparison: () -> Unit,
    onBeginGuidedTest: () -> Unit,
    onArmGuidedTest: () -> Unit,
    onFinishGuidedTestPhase: () -> Unit,
    onCancelGuidedTest: () -> Unit,
    onStartExtendedRecording: () -> Unit,
    onStopExtendedRecording: () -> Unit,
) {
    var destinationName by rememberSaveable {
        mutableStateOf(AppDestination.DASHBOARD.name)
    }
    val destination = AppDestination.entries.firstOrNull { it.name == destinationName }
        ?: AppDestination.DASHBOARD

    BackHandler(enabled = destination != AppDestination.DASHBOARD) {
        when (destination) {
            AppDestination.DETAIL -> {
                onClearSelectedSession()
                destinationName = AppDestination.SESSIONS.name
            }
            AppDestination.COMPARISON -> {
                onClearComparison()
                destinationName = AppDestination.SESSIONS.name
            }
            AppDestination.GUIDED_TEST -> {
                onCancelGuidedTest()
                destinationName = AppDestination.SESSIONS.name
            }
            AppDestination.SESSIONS -> destinationName = AppDestination.DASHBOARD.name
            AppDestination.DASHBOARD -> Unit
        }
    }

    when (destination) {
        AppDestination.DASHBOARD -> ChargeDashboard(
            state = state,
            onOpenSessions = { destinationName = AppDestination.SESSIONS.name },
            onNameAccessory = onNameAccessory,
            onStartExtendedRecording = onStartExtendedRecording,
            onStopExtendedRecording = onStopExtendedRecording,
        )

        AppDestination.SESSIONS -> SessionHistoryScreen(
            summaries = state.sessionSummaries,
            activeSessionId = state.activeSessionId,
            onBack = { destinationName = AppDestination.DASHBOARD.name },
            onOpenSession = { sessionId ->
                onSelectSession(sessionId)
                destinationName = AppDestination.DETAIL.name
            },
            onCompareSessions = { sessionIds ->
                onSelectComparison(sessionIds)
                destinationName = AppDestination.COMPARISON.name
            },
            onStartGuidedTest = {
                onBeginGuidedTest()
                destinationName = AppDestination.GUIDED_TEST.name
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

        AppDestination.COMPARISON -> SessionComparisonScreen(
            sessions = state.comparisonSessions,
            onBack = {
                onClearComparison()
                destinationName = AppDestination.SESSIONS.name
            },
        )

        AppDestination.GUIDED_TEST -> GuidedTestScreen(
            test = state.guidedTest,
            reading = state.reading,
            accessoryName = state.currentAccessory?.name,
            comparisonReady = state.comparisonSessions.size == 2,
            onBack = {
                onCancelGuidedTest()
                destinationName = AppDestination.SESSIONS.name
            },
            onArm = onArmGuidedTest,
            onFinishPhase = onFinishGuidedTestPhase,
            onViewResults = { destinationName = AppDestination.COMPARISON.name },
        )
    }
}

@Composable
private fun ComparisonPicker(
    comparisonMode: Boolean,
    selectedCount: Int,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onCompare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(18.dp))
            .border(1.dp, DividerGreen, RoundedCornerShape(18.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (comparisonMode) "SELECT TWO SESSIONS" else "COMPARE CHARGING",
                style = MaterialTheme.typography.labelMedium,
                color = EnergyCyan,
            )
            Text(
                text = if (comparisonMode) {
                    "$selectedCount / 2 selected"
                } else {
                    "Overlay normalized power and temperature"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
        }
        if (comparisonMode) {
            Text(
                text = "CANCEL",
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
            )
            Text(
                text = "COMPARE",
                modifier = Modifier
                    .clickable(enabled = selectedCount == 2, onClick = onCompare)
                    .padding(8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (selectedCount == 2) EnergyGreen else TextSecondary,
            )
        } else {
            Text(
                text = "START  ›",
                modifier = Modifier
                    .clickable(onClick = onStart)
                    .padding(8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = EnergyGreen,
            )
        }
    }
}

@Composable
private fun GuidedTestLaunchCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF132720), RoundedCornerShape(18.dp))
            .border(1.dp, EnergyGreen.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "GUIDED A/B CHARGING TEST",
                style = MaterialTheme.typography.labelMedium,
                color = EnergyGreen,
            )
            Text(
                text = "Run two controlled five-minute tests",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
        }
        Text(
            text = "START  ›",
            style = MaterialTheme.typography.labelLarge,
            color = EnergyGreen,
        )
    }
}

@Composable
private fun GuidedTestScreen(
    test: GuidedChargingTestState,
    reading: com.nicky.chargemonitor.battery.BatteryReading?,
    accessoryName: String?,
    comparisonReady: Boolean,
    onBack: () -> Unit,
    onArm: () -> Unit,
    onFinishPhase: () -> Unit,
    onViewResults: () -> Unit,
) {
    var clockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val runningStart = when (test.phase) {
        GuidedTestPhase.RUNNING_A -> test.firstRun?.startedAtMillis
        GuidedTestPhase.RUNNING_B -> test.secondRun?.startedAtMillis
        else -> null
    }
    LaunchedEffect(test.phase, runningStart) {
        while (runningStart != null) {
            clockMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val elapsedMillis = runningStart?.let { (clockMillis - it).coerceAtLeast(0L) } ?: 0L
    val connected = reading?.isPowerConnected == true

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
                    eyebrow = "CONTROLLED DIAGNOSTIC",
                    title = "A/B charging test",
                    onBack = onBack,
                )
            }
            item {
                GuidedPhaseHero(
                    test = test,
                    connected = connected,
                    accessoryName = accessoryName,
                    elapsedMillis = elapsedMillis,
                )
            }
            item {
                GuidedLiveConditions(
                    reading = reading,
                    accessoryName = accessoryName,
                )
            }
            item {
                GuidedTestAction(
                    test = test,
                    connected = connected,
                    comparisonReady = comparisonReady,
                    onArm = onArm,
                    onFinishPhase = onFinishPhase,
                    onViewResults = onViewResults,
                )
            }
            item {
                GuidedTestInstructions(test.phase)
            }
        }
    }
}

@Composable
private fun GuidedPhaseHero(
    test: GuidedChargingTestState,
    connected: Boolean,
    accessoryName: String?,
    elapsedMillis: Long,
) {
    val title = when (test.phase) {
        GuidedTestPhase.PREPARE_A -> "Prepare charger A"
        GuidedTestPhase.WAITING_FOR_A -> "Connect charger A"
        GuidedTestPhase.RUNNING_A -> "Testing A · ${accessoryName ?: "Current source"}"
        GuidedTestPhase.PREPARE_B -> "Prepare charger B"
        GuidedTestPhase.WAITING_FOR_B -> "Connect charger B"
        GuidedTestPhase.RUNNING_B -> "Testing B · ${accessoryName ?: "Current source"}"
        GuidedTestPhase.COMPLETE -> "Both test windows captured"
        GuidedTestPhase.IDLE -> "Ready to begin"
    }
    val detail = when (test.phase) {
        GuidedTestPhase.PREPARE_A ->
            if (connected) "Disconnect the current charger before arming test A."
            else "Keep the phone cool and note the current battery level."
        GuidedTestPhase.WAITING_FOR_A -> "The timer starts automatically when power connects."
        GuidedTestPhase.RUNNING_A -> "Let the phone remain still for five minutes."
        GuidedTestPhase.PREPARE_B ->
            if (connected) "Disconnect charger A, then connect charger B after arming."
            else "Use the same phone position and similar temperature."
        GuidedTestPhase.WAITING_FOR_B -> "The second timer starts when power connects."
        GuidedTestPhase.RUNNING_B -> "Match the duration and conditions from test A."
        GuidedTestPhase.COMPLETE -> "The results use only the captured test windows."
        GuidedTestPhase.IDLE -> "Two controlled traces will be compared."
    }
    val running = test.phase == GuidedTestPhase.RUNNING_A ||
        test.phase == GuidedTestPhase.RUNNING_B
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (running) Color(0xFF0E2B20) else CardBackground,
                RoundedCornerShape(27.dp),
            )
            .border(
                1.dp,
                if (running) EnergyGreen.copy(alpha = 0.42f) else DividerGreen,
                RoundedCornerShape(27.dp),
            )
            .padding(20.dp),
    ) {
        Text(
            text = "STEP ${test.phase.stepNumber()} OF 4",
            style = MaterialTheme.typography.labelMedium,
            color = EnergyGreen,
        )
        Spacer(Modifier.height(6.dp))
        Text(text = title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(text = detail, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        if (running) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = elapsedMillis.formatDuration(),
                style = MaterialTheme.typography.displaySmall,
                color = EnergyGreen,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(DividerGreen, RoundedCornerShape(50)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            (elapsedMillis.toFloat() / GUIDED_TEST_TARGET_MILLIS)
                                .coerceIn(0f, 1f),
                        )
                        .height(8.dp)
                        .background(EnergyGreen, RoundedCornerShape(50)),
                )
            }
            Text(
                text = "Recommended window · 5 minutes",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun GuidedLiveConditions(
    reading: com.nicky.chargemonitor.battery.BatteryReading?,
    accessoryName: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = "LIVE CONDITIONS",
            modifier = Modifier.padding(start = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ReportStat(
                label = "Battery",
                value = reading?.levelPercent?.let { "$it%" } ?: "—",
                accent = WarningAmber,
                modifier = Modifier.weight(1f),
            )
            ReportStat(
                label = "Temperature",
                value = reading?.temperatureCelsius.formatMetric("°C", 1),
                accent = Color(0xFFFF8FA3),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ReportStat(
                label = "Source",
                value = accessoryName ?: reading?.powerSource?.label ?: "Disconnected",
                accent = EnergyCyan,
                modifier = Modifier.weight(1f),
            )
            ReportStat(
                label = "Net power",
                value = reading?.powerWatts.formatMetric("W", 2),
                accent = EnergyGreen,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GuidedTestAction(
    test: GuidedChargingTestState,
    connected: Boolean,
    comparisonReady: Boolean,
    onArm: () -> Unit,
    onFinishPhase: () -> Unit,
    onViewResults: () -> Unit,
) {
    val label: String
    val enabled: Boolean
    val action: () -> Unit
    when (test.phase) {
        GuidedTestPhase.PREPARE_A -> {
            label = if (connected) "DISCONNECT TO CONTINUE" else "ARM TEST A"
            enabled = !connected
            action = onArm
        }
        GuidedTestPhase.PREPARE_B -> {
            label = if (connected) "DISCONNECT TO CONTINUE" else "ARM TEST B"
            enabled = !connected
            action = onArm
        }
        GuidedTestPhase.RUNNING_A, GuidedTestPhase.RUNNING_B -> {
            label = "FINISH THIS TEST WINDOW"
            enabled = true
            action = onFinishPhase
        }
        GuidedTestPhase.COMPLETE -> {
            label = if (comparisonReady) "VIEW COMPARISON" else "PREPARING RESULTS…"
            enabled = comparisonReady
            action = onViewResults
        }
        else -> {
            label = "WAITING FOR POWER…"
            enabled = false
            action = {}
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(
                if (enabled) EnergyGreen else CardBackgroundElevated,
                RoundedCornerShape(17.dp),
            )
            .clickable(enabled = enabled, onClick = action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) AppBackground else TextSecondary,
        )
    }
}

@Composable
private fun GuidedTestInstructions(phase: GuidedTestPhase) {
    val instructions = when (phase) {
        GuidedTestPhase.PREPARE_A, GuidedTestPhase.WAITING_FOR_A -> listOf(
            "Prefer a battery level below 70% to avoid normal charge taper.",
            "Remove a thick case if you want to isolate thermal effects.",
            "Keep screen brightness and phone usage consistent.",
        )
        GuidedTestPhase.PREPARE_B, GuidedTestPhase.WAITING_FOR_B -> listOf(
            "Allow temperature to return close to test A's starting value.",
            "Keep the same phone position and screen conditions.",
            "For wireless pads, align the phone consistently.",
        )
        GuidedTestPhase.RUNNING_A, GuidedTestPhase.RUNNING_B -> listOf(
            "Do not move the phone or launch other apps.",
            "Five minutes provides a stronger comparison; one minute is the minimum useful trace.",
        )
        GuidedTestPhase.COMPLETE -> listOf(
            "The comparison confidence score reflects differences in level, temperature, source, and duration.",
        )
        GuidedTestPhase.IDLE -> emptyList()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EnergyGreenMuted.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "TEST GUIDANCE",
            style = MaterialTheme.typography.labelMedium,
            color = EnergyGreen,
        )
        instructions.forEach { instruction ->
            Row {
                Text(text = "•", color = EnergyGreen)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun SessionComparisonScreen(
    sessions: List<ChargingSession>,
    onBack: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ScreenHeader(
                    eyebrow = "A / B ANALYSIS",
                    title = "Session comparison",
                    onBack = onBack,
                )
            }
            if (sessions.size < 2) {
                item {
                    EmptySessionsCard()
                }
            } else {
                val first = sessions[0]
                val second = sessions[1]
                val comparison = SessionComparator.compare(first, second)
                item {
                    ComparisonHero(
                        comparison = comparison,
                        firstName = first.comparisonName("Session A"),
                        secondName = second.comparisonName("Session B"),
                    )
                }
                item {
                    ComparisonMetricsCard(
                        first = first,
                        second = second,
                        comparison = comparison,
                    )
                }
                item {
                    DualTraceCard(
                        title = "NORMALIZED POWER",
                        unit = "W",
                        includeZero = true,
                        firstSamples = comparison.firstComparableTrace,
                        secondSamples = comparison.secondComparableTrace,
                        value = { it.powerAverageWatts },
                    )
                }
                item {
                    DualTraceCard(
                        title = "TEMPERATURE",
                        unit = "°C",
                        includeZero = false,
                        firstSamples = comparison.firstComparableTrace,
                        secondSamples = comparison.secondComparableTrace,
                        value = { it.temperatureCelsius },
                    )
                }
                item {
                    ComparisonConditionsCard(
                        first = first,
                        second = second,
                        confidence = comparison.confidenceScore,
                    )
                }
                item { DiagnosticLimitNote() }
            }
        }
    }
}

@Composable
private fun ComparisonHero(
    comparison: com.nicky.chargemonitor.session.SessionComparison,
    firstName: String,
    secondName: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF10272D), RoundedCornerShape(26.dp))
            .border(1.dp, EnergyCyan.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
            .padding(20.dp),
    ) {
        Text(
            text = "COMPARISON RESULT · ${comparison.confidenceScore}/10 CONFIDENCE",
            style = MaterialTheme.typography.labelMedium,
            color = EnergyCyan,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = comparison.headline,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = comparison.detail,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ChartLegend(EnergyGreen, "A · $firstName")
            ChartLegend(EnergyCyan, "B · $secondName")
        }
    }
}

@Composable
private fun ComparisonMetricsCard(
    first: ChargingSession,
    second: ChargingSession,
    comparison: com.nicky.chargemonitor.session.SessionComparison,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(23.dp))
            .border(1.dp, DividerGreen, RoundedCornerShape(23.dp))
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "COMPARABLE METRICS",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
        ComparisonMetricRow(
            label = "Average power",
            first = comparison.first.averagePowerWatts.formatMetric("W", 2),
            second = comparison.second.averagePowerWatts.formatMetric("W", 2),
        )
        ComparisonMetricRow(
            label = "Low 10%",
            first = comparison.first.tenthPercentilePowerWatts.formatMetric("W", 2),
            second = comparison.second.tenthPercentilePowerWatts.formatMetric("W", 2),
        )
        ComparisonMetricRow(
            label = "Peak temperature",
            first = comparison.first.peakTemperatureCelsius.formatMetric("°C", 1),
            second = comparison.second.peakTemperatureCelsius.formatMetric("°C", 1),
        )
        ComparisonMetricRow(
            label = "Battery range",
            first = SessionAnalyzer.summarize(first).levelRange(),
            second = SessionAnalyzer.summarize(second).levelRange(),
        )
    }
}

@Composable
private fun ComparisonMetricRow(
    label: String,
    first: String,
    second: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackgroundElevated, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Text(
            text = first,
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.titleSmall,
            color = EnergyGreen,
        )
        Text(
            text = second,
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.titleSmall,
            color = EnergyCyan,
        )
    }
}

@Composable
private fun DualTraceCard(
    title: String,
    unit: String,
    includeZero: Boolean,
    firstSamples: List<com.nicky.chargemonitor.session.DiagnosticSample>,
    secondSamples: List<com.nicky.chargemonitor.session.DiagnosticSample>,
    value: (com.nicky.chargemonitor.session.DiagnosticSample) -> Double?,
) {
    val firstValues = firstSamples.mapNotNull(value)
    val secondValues = secondSamples.mapNotNull(value)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(23.dp))
            .border(1.dp, DividerGreen, RoundedCornerShape(23.dp))
            .padding(17.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(
                text = "${firstValues.maxOrNull().formatMetric(unit, 1)} / " +
                    secondValues.maxOrNull().formatMetric(unit, 1),
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
            )
        }
        Spacer(Modifier.height(13.dp))
        DualTraceChart(
            firstValues = firstValues,
            secondValues = secondValues,
            includeZero = includeZero,
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp),
        )
    }
}

@Composable
private fun DualTraceChart(
    firstValues: List<Double>,
    secondValues: List<Double>,
    includeZero: Boolean,
    modifier: Modifier = Modifier,
) {
    val first = remember(firstValues) { firstValues.downsample(350) }
    val second = remember(secondValues) { secondValues.downsample(350) }
    Canvas(modifier = modifier) {
        repeat(4) { row ->
            val y = size.height * row / 3f
            drawLine(
                color = DividerGreen.copy(alpha = 0.62f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        val all = first + second
        if (all.isEmpty()) return@Canvas
        val rawMin = if (includeZero) {
            min(0.0, all.minOrNull() ?: 0.0)
        } else {
            all.minOrNull() ?: 0.0
        }
        val rawMax = max(1.0, all.maxOrNull() ?: 1.0)
        val padding = max(0.5, (rawMax - rawMin) * 0.1)
        val chartMin = rawMin - padding
        val range = rawMax + padding - chartMin

        fun drawValues(values: List<Double>, color: Color) {
            if (values.size < 2) return
            val path = Path()
            values.forEachIndexed { index, reading ->
                val x = size.width * index / (values.size - 1).toFloat()
                val y = (size.height * (1.0 - (reading - chartMin) / range)).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        drawValues(first, EnergyGreen)
        drawValues(second, EnergyCyan)
    }
}

@Composable
private fun ComparisonConditionsCard(
    first: ChargingSession,
    second: ChargingSession,
    confidence: Int,
) {
    val firstSummary = SessionAnalyzer.summarize(first)
    val secondSummary = SessionAnalyzer.summarize(second)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(20.dp))
            .border(1.dp, DividerGreen, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Text(
            text = "TEST QUALITY",
            style = MaterialTheme.typography.labelMedium,
            color = if (confidence >= 8) EnergyGreen else WarningAmber,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Confidence improves when battery level, starting temperature, connection " +
                "type, and test duration are similar.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        ComparisonMetricRow(
            label = "Source",
            first = firstSummary.source.label,
            second = secondSummary.source.label,
        )
        Spacer(Modifier.height(7.dp))
        ComparisonMetricRow(
            label = "Buckets",
            first = first.samples.size.toString(),
            second = second.samples.size.toString(),
        )
    }
}

@Composable
private fun SessionHistoryScreen(
    summaries: List<ChargingSessionSummary>,
    activeSessionId: String?,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onCompareSessions: (List<String>) -> Unit,
    onStartGuidedTest: () -> Unit,
) {
    var comparisonMode by remember { mutableStateOf(false) }
    var selectedForComparison by remember { mutableStateOf<Set<String>>(emptySet()) }
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
            if (summaries.size >= 2) {
                item {
                    ComparisonPicker(
                        comparisonMode = comparisonMode,
                        selectedCount = selectedForComparison.size,
                        onStart = {
                            comparisonMode = true
                            selectedForComparison = emptySet()
                        },
                        onCancel = {
                            comparisonMode = false
                            selectedForComparison = emptySet()
                        },
                        onCompare = {
                            onCompareSessions(selectedForComparison.toList())
                        },
                    )
                }
            }
            item {
                GuidedTestLaunchCard(onClick = onStartGuidedTest)
            }
            if (summaries.isEmpty()) {
                item { EmptySessionsCard() }
            } else {
                summaries.forEach { summary ->
                    item(key = summary.id) {
                        SessionSummaryCard(
                            summary = summary,
                            isRecording = summary.id == activeSessionId,
                            comparisonLabel = selectedForComparison
                                .indexOf(summary.id)
                                .takeIf { it >= 0 }
                                ?.let { if (it == 0) "A" else "B" },
                            onClick = {
                                if (comparisonMode) {
                                    selectedForComparison =
                                        if (summary.id in selectedForComparison) {
                                            selectedForComparison - summary.id
                                        } else if (selectedForComparison.size < 2) {
                                            selectedForComparison + summary.id
                                        } else {
                                            selectedForComparison
                                        }
                                } else {
                                    onOpenSession(summary.id)
                                }
                            },
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
    comparisonLabel: String?,
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
                    text = "${summary.accessoryName ?: summary.source.label} · " +
                        summary.durationMillis.formatDuration(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Text(
                text = comparisonLabel ?: if (isRecording) "REC" else summary.levelRange(),
                modifier = Modifier
                    .background(
                        if (isRecording) EnergyGreen.copy(alpha = 0.14f)
                        else CardBackgroundElevated,
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (isRecording || comparisonLabel != null) EnergyGreen else TextPrimary,
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
        if (summary.accessoryName != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ReportStat(
                    "Accessory",
                    summary.accessoryName,
                    EnergyGreen,
                    Modifier.weight(1f),
                )
                ReportStat(
                    "Connection",
                    summary.source.label,
                    EnergyCyan,
                    Modifier.weight(1f),
                )
            }
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
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ReportStat(
                "Peak temperature",
                summary.peakTemperatureCelsius.formatMetric("°C", 1),
                Color(0xFFFF8FA3),
                Modifier.weight(1f),
            )
            ReportStat(
                "Thermal forecast",
                summary.peakThermalHeadroomForecast?.let {
                    "${(it * 100.0).toInt()}% severe"
                } ?: "Unavailable",
                WarningAmber,
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

private fun ChargingSession.comparisonName(fallback: String): String =
    accessoryName ?: "${initialSource.label} $fallback"

private fun GuidedTestPhase.stepNumber(): Int = when (this) {
    GuidedTestPhase.IDLE, GuidedTestPhase.PREPARE_A, GuidedTestPhase.WAITING_FOR_A -> 1
    GuidedTestPhase.RUNNING_A -> 2
    GuidedTestPhase.PREPARE_B, GuidedTestPhase.WAITING_FOR_B -> 3
    GuidedTestPhase.RUNNING_B, GuidedTestPhase.COMPLETE -> 4
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
private const val GUIDED_TEST_TARGET_MILLIS = 5 * 60_000L
