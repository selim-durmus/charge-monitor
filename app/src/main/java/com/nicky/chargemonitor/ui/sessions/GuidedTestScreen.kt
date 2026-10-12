package com.nicky.chargemonitor.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nicky.chargemonitor.battery.BatteryReading
import com.nicky.chargemonitor.session.GUIDED_TEST_WINDOW_MILLIS
import com.nicky.chargemonitor.session.GuidedChargingTestState
import com.nicky.chargemonitor.session.GuidedTestPhase
import com.nicky.chargemonitor.ui.components.KeepScreenOn
import com.nicky.chargemonitor.ui.components.ScreenScaffold
import com.nicky.chargemonitor.ui.components.SectionCard
import com.nicky.chargemonitor.ui.components.SectionLabel
import com.nicky.chargemonitor.ui.components.StatTile
import com.nicky.chargemonitor.ui.components.formatClock
import com.nicky.chargemonitor.ui.components.formatUnit
import com.nicky.chargemonitor.ui.components.formatPercent
import com.nicky.chargemonitor.ui.components.screenContent
import com.nicky.chargemonitor.ui.components.shortLabel
import com.nicky.chargemonitor.ui.theme.ChargeTheme
import kotlinx.coroutines.delay

@Composable
fun GuidedTestScreen(
    test: GuidedChargingTestState,
    reading: BatteryReading?,
    accessoryName: String?,
    comparisonReady: Boolean,
    onBack: () -> Unit,
    onArm: () -> Unit,
    onFinishWindow: () -> Unit,
    onViewResults: () -> Unit,
    onRestart: () -> Unit,
) {
    // Sampling (and therefore the test) stops when the screen turns off.
    KeepScreenOn(enabled = test.isActive)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val runStart = test.currentRun?.startedAtMillis
    LaunchedEffect(runStart) {
        while (runStart != null) {
            now = System.currentTimeMillis()
            delay(500L)
        }
    }
    val elapsed = runStart?.let { (now - it).coerceIn(0L, GUIDED_TEST_WINDOW_MILLIS) } ?: 0L
    val connected = reading?.isPowerConnected == true
    val sourceName = accessoryName ?: reading?.powerSource?.takeIf { connected }?.shortLabel

    ScreenScaffold(
        title = "A/B charger test",
        subtitle = "Two equal ${GUIDED_TEST_WINDOW_MILLIS / 60_000}-minute windows",
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            contentPadding = padding.screenContent(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "stepper") { Stepper(test.phase) }
            item(key = "phase") {
                PhaseCard(
                    test = test,
                    connected = connected,
                    sourceName = sourceName,
                    elapsedMillis = elapsed,
                    comparisonReady = comparisonReady,
                    onArm = onArm,
                    onFinishWindow = onFinishWindow,
                    onViewResults = onViewResults,
                    onRestart = onRestart,
                )
            }
            item(key = "conditions") { LiveConditions(reading) }
            item(key = "tips") { Tips(test.phase) }
        }
    }
}

private val StepLabels = listOf("Prep A", "Test A", "Prep B", "Test B")

private fun GuidedTestPhase.stepIndex(): Int = when (this) {
    GuidedTestPhase.IDLE, GuidedTestPhase.PREPARE_A, GuidedTestPhase.WAITING_FOR_A -> 0
    GuidedTestPhase.RUNNING_A -> 1
    GuidedTestPhase.PREPARE_B, GuidedTestPhase.WAITING_FOR_B -> 2
    GuidedTestPhase.RUNNING_B -> 3
    GuidedTestPhase.COMPLETE -> 4
}

@Composable
private fun Stepper(phase: GuidedTestPhase) {
    val current = phase.stepIndex()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StepLabels.forEachIndexed { index, label ->
            val color = when {
                index < current -> MaterialTheme.colorScheme.primary
                index == current -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(color, RoundedCornerShape(50)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (index == current) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun PhaseCard(
    test: GuidedChargingTestState,
    connected: Boolean,
    sourceName: String?,
    elapsedMillis: Long,
    comparisonReady: Boolean,
    onArm: () -> Unit,
    onFinishWindow: () -> Unit,
    onViewResults: () -> Unit,
    onRestart: () -> Unit,
) {
    val (title, detail) = when (test.phase) {
        GuidedTestPhase.IDLE, GuidedTestPhase.PREPARE_A ->
            "Get charger A ready" to if (connected) {
                "Unplug the phone first so the test starts from a clean connection."
            } else {
                "When you're ready, start the test and then plug in charger A."
            }
        GuidedTestPhase.WAITING_FOR_A -> "Plug in charger A" to "The timer starts as soon as power is detected."
        GuidedTestPhase.RUNNING_A -> "Testing A${sourceName?.let { " · $it" }.orEmpty()}" to
            "Leave the phone still. The window closes on its own at 5:00."
        GuidedTestPhase.PREPARE_B -> "Get charger B ready" to if (connected) {
            "Unplug charger A. Let the phone cool toward its starting temperature."
        } else {
            "Use the same position and screen state as test A, then start."
        }
        GuidedTestPhase.WAITING_FOR_B -> "Plug in charger B" to "The timer starts as soon as power is detected."
        GuidedTestPhase.RUNNING_B -> "Testing B${sourceName?.let { " · $it" }.orEmpty()}" to
            "Match test A's conditions. The window closes on its own at 5:00."
        GuidedTestPhase.COMPLETE -> "Both windows captured" to
            "Results compare only the captured windows, matched by battery level where possible."
    }
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (test.isRunning) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    elapsedMillis.formatClock(),
                    style = MaterialTheme.typography.displayMedium,
                    color = ChargeTheme.colors.charging,
                )
                Text(
                    " / ${GUIDED_TEST_WINDOW_MILLIS.formatClock()}",
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { elapsedMillis.toFloat() / GUIDED_TEST_WINDOW_MILLIS },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(20.dp))
        when (test.phase) {
            GuidedTestPhase.IDLE, GuidedTestPhase.PREPARE_A, GuidedTestPhase.PREPARE_B -> Button(
                onClick = onArm,
                enabled = !connected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        connected -> "Unplug to continue"
                        test.phase == GuidedTestPhase.PREPARE_B -> "Start test B"
                        else -> "Start test A"
                    },
                )
            }

            GuidedTestPhase.WAITING_FOR_A, GuidedTestPhase.WAITING_FOR_B -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Waiting for power…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            GuidedTestPhase.RUNNING_A, GuidedTestPhase.RUNNING_B -> OutlinedButton(
                onClick = onFinishWindow,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("End window early") }

            GuidedTestPhase.COMPLETE -> {
                Button(
                    onClick = onViewResults,
                    enabled = comparisonReady,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (comparisonReady) "View results" else "Preparing results…") }
                TextButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                    Text("Run another test")
                }
            }
        }
    }
}

@Composable
private fun LiveConditions(reading: BatteryReading?) {
    Column {
        SectionLabel("Right now", modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Battery", reading?.levelPercent.formatPercent(), Modifier.weight(1f))
            StatTile("Temp", reading?.temperatureCelsius.formatUnit(1, "°C"), Modifier.weight(1f))
            StatTile("Power", reading?.powerWatts.formatUnit(1, "W"), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tips(phase: GuidedTestPhase) {
    val tips = when (phase) {
        GuidedTestPhase.IDLE, GuidedTestPhase.PREPARE_A, GuidedTestPhase.WAITING_FOR_A -> listOf(
            "Start below 70% so normal top-up tapering doesn't skew the result.",
            "Keep brightness and app usage the same for both tests.",
        )
        GuidedTestPhase.PREPARE_B, GuidedTestPhase.WAITING_FOR_B -> listOf(
            "Wait until the temperature is close to where test A started.",
            "On wireless pads, align the phone the same way as before.",
        )
        GuidedTestPhase.RUNNING_A, GuidedTestPhase.RUNNING_B -> listOf(
            "The screen stays on during the test; avoid touching the phone.",
        )
        GuidedTestPhase.COMPLETE -> emptyList()
    }
    if (tips.isEmpty()) return
    Column(Modifier.padding(horizontal = 4.dp)) {
        tips.forEach { tip ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(
                    "•",
                    Modifier.width(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    tip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
