package com.nicky.chargemonitor.ui.live

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nicky.chargemonitor.DashboardState
import com.nicky.chargemonitor.POWER_HISTORY_WINDOW_MILLIS
import com.nicky.chargemonitor.R
import com.nicky.chargemonitor.accessory.AccessoryBaseline
import com.nicky.chargemonitor.accessory.AccessoryProfile
import com.nicky.chargemonitor.battery.BatteryReading
import com.nicky.chargemonitor.battery.ChargeState
import com.nicky.chargemonitor.battery.ThermalStatus
import com.nicky.chargemonitor.session.ChargingSessionSummary
import com.nicky.chargemonitor.ui.components.AppIcon
import com.nicky.chargemonitor.ui.components.BatteryRing
import com.nicky.chargemonitor.ui.components.ExpandableCard
import com.nicky.chargemonitor.ui.components.FootNote
import com.nicky.chargemonitor.ui.components.KeyValueRow
import com.nicky.chargemonitor.ui.components.LivePowerChart
import com.nicky.chargemonitor.ui.components.NO_VALUE
import com.nicky.chargemonitor.ui.components.Pill
import com.nicky.chargemonitor.ui.components.ScreenScaffold
import com.nicky.chargemonitor.ui.components.SectionCard
import com.nicky.chargemonitor.ui.components.StatTile
import com.nicky.chargemonitor.ui.components.formatDuration
import com.nicky.chargemonitor.ui.components.formatNumber
import com.nicky.chargemonitor.ui.components.formatShortDuration
import com.nicky.chargemonitor.ui.components.formatSigned
import com.nicky.chargemonitor.ui.components.formatUnit
import com.nicky.chargemonitor.ui.components.formatWatts
import com.nicky.chargemonitor.ui.components.notableLabel
import com.nicky.chargemonitor.ui.components.screenContent
import com.nicky.chargemonitor.ui.components.shortLabel
import com.nicky.chargemonitor.ui.theme.ChargeTheme

@Composable
fun LiveScreen(
    state: DashboardState,
    onOpenSession: (String) -> Unit,
    onOpenSessions: () -> Unit,
    onNameAccessory: (String) -> Unit,
    onStartExtendedRecording: () -> Unit,
    onStopExtendedRecording: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val reading = state.reading
    val connected = reading?.isPowerConnected == true
    ScreenScaffold(
        title = "Charge Monitor",
        actions = { LiveIndicator(isLive = state.isSampling) },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            contentPadding = padding.screenContent(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") { HeroCard(reading) }
            if (reading != null && reading.isRunningWarm()) {
                item(key = "thermal") { ThermalWarning(reading) }
            }
            item(key = "chart") { PowerCard(state) }
            item(key = "stats") { QuickStats(reading) }
            item(key = "session") {
                if (connected) {
                    ChargerSessionCard(
                        reading = reading,
                        profile = state.currentAccessory,
                        baseline = state.currentAccessoryBaseline,
                        activeSession = state.activeSessionSummary,
                        extendedRecordingActive = state.extendedRecordingActive,
                        onNameAccessory = onNameAccessory,
                        onOpenSession = onOpenSession,
                        onStartExtendedRecording = onStartExtendedRecording,
                        onStopExtendedRecording = onStopExtendedRecording,
                    )
                } else {
                    UnpluggedCard(
                        sessionCount = state.sessionSummaries.size,
                        onOpenSessions = onOpenSessions,
                    )
                }
            }
            item(key = "details") { BatteryDetails(reading) }
        }
    }
}

@Composable
private fun LiveIndicator(isLive: Boolean) {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "live alpha",
    )
    Pill(
        text = if (isLive) "Live" else "Paused",
        modifier = Modifier.padding(end = 12.dp),
        color = if (isLive) ChargeTheme.colors.charging else MaterialTheme.colorScheme.onSurfaceVariant,
        showDot = true,
        dotAlpha = if (isLive) alpha else 1f,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(reading: BatteryReading?) {
    val colors = ChargeTheme.colors
    val connected = reading?.isPowerConnected == true
    val watts = reading?.powerWatts
    val accent = when {
        reading == null -> MaterialTheme.colorScheme.onSurfaceVariant
        reading.isRunningWarm() -> colors.warning
        connected -> colors.charging
        else -> colors.discharging
    }
    val animatedWatts by animateFloatAsState(
        targetValue = reading?.powerMagnitudeWatts?.toFloat() ?: 0f,
        animationSpec = tween(160),
        label = "hero watts",
    )
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            BatteryRing(
                levelPercent = reading?.levelPercent,
                color = accent,
                flowing = connected && (watts ?: 0.0) > 0.05,
                modifier = Modifier.size(216.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (watts == null) NO_VALUE else animatedWatts.toDouble().formatWatts(),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = flowLabel(reading),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = listOfNotNull(
                    reading?.levelPercent?.let { "$it%" },
                    reading?.chargeState?.label ?: "Reading battery…",
                ).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
            )
            if (reading != null) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill(reading.powerSource.shortLabel)
                    reading.chargingProfile.takeIf { connected }?.notableLabel?.let { Pill(it) }
                    reading.chargeTimeRemainingMillis
                        ?.takeIf { connected && reading.chargeState == ChargeState.CHARGING && it > 0 }
                        ?.let { Pill("Full in ${it.formatShortDuration()}") }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun flowLabel(reading: BatteryReading?): String {
    val watts = reading?.powerWatts ?: return "watts"
    return when {
        reading.chargeState == ChargeState.FULL && watts < 0.5 -> "watts · battery full"
        watts > 0.05 -> "watts into battery"
        watts < -0.05 -> "watts from battery"
        else -> "watts · idle"
    }
}

/** A forecast of 1.0 is Android's own severe-throttling threshold. */
private fun BatteryReading.isRunningWarm(): Boolean =
    thermalStatus.severity >= ThermalStatus.LIGHT.severity ||
        (thermalHeadroomForecast30Seconds ?: 0.0) >= 1.0

@Composable
private fun ThermalWarning(reading: BatteryReading) {
    val warning = ChargeTheme.colors.warning
    SectionCard(color = warning.copy(alpha = 0.12f)) {
        Row(verticalAlignment = Alignment.Top) {
            AppIcon(R.drawable.ic_warning, contentDescription = null, tint = warning)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Phone is running warm", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = buildString {
                        if (reading.thermalStatus.severity >= ThermalStatus.LIGHT.severity) {
                            append("Android reports ${reading.thermalStatus.label.lowercase()}. ")
                        } else {
                            append("Android expects throttling within 30 seconds. ")
                        }
                        append("Charging may slow down to protect the battery.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PowerCard(state: DashboardState) {
    val color = if (state.reading?.isPowerConnected == true) {
        ChargeTheme.colors.charging
    } else {
        ChargeTheme.colors.discharging
    }
    val peak = state.powerHistory.maxOfOrNull { it.watts }
    SectionCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Power", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Last 30 seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (peak != null && peak > 0) {
                Text(
                    text = "Peak ${peak.formatUnit(1, "W")}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LivePowerChart(
            points = state.powerHistory,
            windowMillis = POWER_HISTORY_WINDOW_MILLIS,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )
    }
}

@Composable
private fun QuickStats(reading: BatteryReading?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            label = "Current",
            value = reading?.currentAmps.formatSigned(2, "A"),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Voltage",
            value = reading?.voltageVolts.formatUnit(2, "V"),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Temp",
            value = reading?.temperatureCelsius.formatUnit(1, "°C"),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChargerSessionCard(
    reading: BatteryReading,
    profile: AccessoryProfile?,
    baseline: AccessoryBaseline?,
    activeSession: ChargingSessionSummary?,
    extendedRecordingActive: Boolean,
    onNameAccessory: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onStartExtendedRecording: () -> Unit,
    onStopExtendedRecording: () -> Unit,
) {
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    SectionCard(contentPadding = 0.dp) {
        // Charger identity and how it usually performs.
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Charger",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = profile?.name ?: "Unnamed ${reading.powerSource.shortLabel.lowercase()} charger",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            TextButton(onClick = { showNameDialog = true }) {
                Text(if (profile == null) "Name it" else "Rename")
            }
        }
        Text(
            text = chargerInsight(reading, profile, baseline),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // The automatically recorded session for this connection.
        if (activeSession != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSession(activeSession.id) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RecordingDot()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Recording this session", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${activeSession.durationMillis.formatDuration()} · " +
                            "avg ${activeSession.averagePowerWatts.formatUnit(1, "W")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppIcon(
                    R.drawable.ic_chevron_right,
                    contentDescription = "Open session",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (extendedRecordingActive) onStopExtendedRecording() else onStartExtendedRecording()
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Keep recording with screen off", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (extendedRecordingActive) {
                        "Recording in the background until you stop or unplug."
                    } else {
                        "Shows a notification while active. Stops when unplugged."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = extendedRecordingActive,
                onCheckedChange = { checked ->
                    if (checked) onStartExtendedRecording() else onStopExtendedRecording()
                },
            )
        }
    }

    if (showNameDialog) {
        NameChargerDialog(
            initialName = profile?.name.orEmpty(),
            onDismiss = { showNameDialog = false },
            onSave = {
                onNameAccessory(it)
                showNameDialog = false
            },
        )
    }
}

private fun chargerInsight(
    reading: BatteryReading,
    profile: AccessoryProfile?,
    baseline: AccessoryBaseline?,
): String {
    val ceiling = reading.sourceCeilingWatts?.let { " Rated up to ${it.formatNumber(0)} W." }.orEmpty()
    return when {
        profile == null -> "Name it to track how this charger performs over time.$ceiling"
        baseline == null -> "A baseline builds after a few minutes of charging below 78%.$ceiling"
        else -> {
            val sessions = if (baseline.sessionCount == 1) "1 session" else "${baseline.sessionCount} sessions"
            "Usually ${baseline.medianPowerWatts.formatUnit(1, "W")} across $sessions.$ceiling"
        }
    }
}

@Composable
private fun RecordingDot() {
    val transition = rememberInfiniteTransition(label = "rec")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "rec alpha",
    )
    Box(
        Modifier
            .size(10.dp)
            .background(MaterialTheme.colorScheme.error.copy(alpha = alpha), CircleShape),
    )
}

@Composable
private fun NameChargerDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this charger") },
        text = {
            Column {
                Text(
                    "The name is reused whenever Android reports the same charger fingerprint.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    placeholder = { Text("e.g. Pixel Stand") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun UnpluggedCard(sessionCount: Int, onOpenSessions: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(R.drawable.ic_bolt, contentDescription = null, tint = ChargeTheme.colors.charging)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Plug in to record a session", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Sessions record automatically while this screen is open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (sessionCount > 0) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOpenSessions, modifier = Modifier.align(Alignment.End)) {
                Text("View $sessionCount past session${if (sessionCount == 1) "" else "s"}")
            }
        }
    }
}

@Composable
private fun BatteryDetails(reading: BatteryReading?) {
    ExpandableCard(
        title = "Battery details",
        subtitle = reading?.let { "Health ${it.health.label.lowercase()} · ${it.thermalStatus.label}" },
    ) {
        KeyValueRow("Health", reading?.health?.label ?: NO_VALUE)
        KeyValueRow("Cycle count", reading?.cycleCount?.toString() ?: "Unavailable")
        KeyValueRow("Chemistry", reading?.technology ?: "Unavailable")
        KeyValueRow(
            "Charging profile",
            if (reading?.isPowerConnected == true) {
                reading.chargingProfile?.description ?: "System managed"
            } else {
                "Not charging"
            },
        )
        KeyValueRow(
            "Average current",
            reading?.averageCurrentMicroamps?.div(1_000_000.0).formatSigned(2, "A"),
        )
        KeyValueRow("Remaining charge", reading?.chargeCounterMilliampHours.formatUnit(0, "mAh"))
        KeyValueRow("Remaining energy", reading?.energyCounterWattHours.formatUnit(2, "Wh"))
        KeyValueRow("Thermal status", reading?.thermalStatus?.label ?: NO_VALUE)
        KeyValueRow(
            "Thermal forecast (30 s)",
            reading?.thermalHeadroomForecast30Seconds
                ?.let { "${(it * 100).toInt()}% of severe limit" }
                ?: "Unavailable",
        )
        KeyValueRow(
            "Charger rating",
            reading?.sourceCeilingWatts?.formatUnit(1, "W") ?: "Not reported",
        )
        Spacer(Modifier.height(8.dp))
        FootNote(
            "Power is measured at the battery (voltage × net current). Android doesn't expose " +
                "the charger's negotiated input power, so phone usage lowers the reading.",
        )
    }
}
