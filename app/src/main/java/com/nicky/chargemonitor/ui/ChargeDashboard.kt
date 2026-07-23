package com.nicky.chargemonitor.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nicky.chargemonitor.DashboardState
import com.nicky.chargemonitor.PowerPoint
import com.nicky.chargemonitor.accessory.AccessoryBaseline
import com.nicky.chargemonitor.accessory.AccessoryProfile
import com.nicky.chargemonitor.battery.BatteryReading
import com.nicky.chargemonitor.battery.ChargeState
import com.nicky.chargemonitor.battery.PowerSource
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun ChargeDashboard(
    state: DashboardState,
    onOpenSessions: () -> Unit,
    onNameAccessory: (String) -> Unit,
    onStartExtendedRecording: () -> Unit,
    onStopExtendedRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                top = 14.dp,
                end = 18.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DashboardHeader(
                    isLive = state.isSampling,
                    intervalMillis = state.sampleIntervalMillis,
                )
            }
            item {
                RecorderStrip(
                    isRecording = state.activeSessionId != null,
                    sessionCount = state.sessionSummaries.size,
                    onClick = onOpenSessions,
                )
            }
            item {
                AccessoryProfileCard(
                    reading = state.reading,
                    profile = state.currentAccessory,
                    baseline = state.currentAccessoryBaseline,
                    onSaveName = onNameAccessory,
                )
            }
            item {
                ScreenOffDiagnosticCard(
                    connected = state.reading?.isPowerConnected == true,
                    active = state.extendedRecordingActive,
                    onStart = onStartExtendedRecording,
                    onStop = onStopExtendedRecording,
                )
            }
            item { HeroCard(reading = state.reading) }
            item {
                LivePowerCard(
                    reading = state.reading,
                    points = state.powerHistory,
                )
            }
            item { MetricsGrid(reading = state.reading) }
            item { BatteryDetails(reading = state.reading) }
            item { MeasurementNote() }
        }
    }
}

@Composable
private fun ScreenOffDiagnosticCard(
    connected: Boolean,
    active: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val actionEnabled = active || connected
    val accent = if (active) WarningAmber else EnergyCyan
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(19.dp))
            .background(
                if (active) WarningAmber.copy(alpha = 0.09f) else CardBackground,
            )
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(19.dp))
            .padding(15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SCREEN-OFF DIAGNOSTIC",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            Text(
                text = when {
                    active -> "Recording continues at 1 sample / sec"
                    connected -> "Capture a complete charging trace"
                    else -> "Connect power to enable"
                },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = if (active) {
                    "A persistent notification is shown until stopped or unplugged."
                } else {
                    "Optional · holds a wake lock only while you explicitly record."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (active) "STOP" else "START",
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = actionEnabled) {
                    if (active) onStop() else onStart()
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (actionEnabled) accent else TextSecondary,
        )
    }
}

@Composable
private fun AccessoryProfileCard(
    reading: BatteryReading?,
    profile: AccessoryProfile?,
    baseline: AccessoryBaseline?,
    onSaveName: (String) -> Unit,
) {
    var showNameDialog by remember { mutableStateOf(false) }
    var accessoryName by remember(profile?.id, showNameDialog) {
        mutableStateOf(profile?.name.orEmpty())
    }
    val connected = reading?.isPowerConnected == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(19.dp))
            .background(CardBackground)
            .border(1.dp, DividerGreen, RoundedCornerShape(19.dp))
            .clickable(enabled = connected) { showNameDialog = true }
            .padding(15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "CHARGER / PAD PROFILE",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            Text(
                text = when {
                    !connected -> "Connect a source to identify it"
                    profile != null -> profile.name
                    else -> "Unnamed ${reading?.powerSource?.label ?: "source"}"
                },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            if (connected) {
                Text(
                    text = baseline?.let {
                        "${it.medianPowerWatts.formatValue(1, "W")} typical · " +
                            "${it.sessionCount} comparable session" +
                            if (it.sessionCount == 1) "" else "s"
                    } ?: "Name this source to begin building its baseline",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        if (connected) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (profile == null) "NAME" else "EDIT",
                style = MaterialTheme.typography.labelLarge,
                color = EnergyGreen,
            )
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = {
                Text(
                    text = "Name this power source",
                    color = TextPrimary,
                )
            },
            text = {
                Column {
                    Text(
                        text = "The name will be reused when Android reports the same source fingerprint.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = accessoryName,
                        onValueChange = { accessoryName = it.take(40) },
                        singleLine = true,
                        label = { Text("Example: Pixel Stand") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = accessoryName.isNotBlank(),
                    onClick = {
                        onSaveName(accessoryName)
                        showNameDialog = false
                    },
                ) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("CANCEL")
                }
            },
            containerColor = CardBackgroundElevated,
        )
    }
}

@Composable
private fun RecorderStrip(
    isRecording: Boolean,
    sessionCount: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(if (isRecording) EnergyGreenMuted.copy(alpha = 0.55f) else CardBackground)
            .border(
                1.dp,
                if (isRecording) EnergyGreen.copy(alpha = 0.28f) else DividerGreen,
                RoundedCornerShape(17.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) EnergyGreen else TextSecondary),
            )
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    text = if (isRecording) "RECORDING DIAGNOSTIC SESSION" else "SESSION RECORDER READY",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isRecording) EnergyGreen else TextSecondary,
                )
                Text(
                    text = "$sessionCount / 5 sessions saved",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
        }
        Text(
            text = "VIEW  ›",
            style = MaterialTheme.typography.labelLarge,
            color = EnergyGreen,
        )
    }
}

@Composable
private fun DashboardHeader(
    isLive: Boolean,
    intervalMillis: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "ENERGY CONSOLE",
                style = MaterialTheme.typography.labelMedium,
                color = EnergyGreen,
            )
            Text(
                text = "Charge Monitor",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
            )
        }
        LiveBadge(isLive = isLive, intervalMillis = intervalMillis)
    }
}

@Composable
private fun LiveBadge(
    isLive: Boolean,
    intervalMillis: Long,
) {
    val pulse = rememberInfiniteTransition(label = "live pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse alpha",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(CardBackgroundElevated)
            .border(1.dp, DividerGreen, RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    if (isLive) EnergyGreen.copy(alpha = pulseAlpha) else TextSecondary,
                ),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = if (isLive) "LIVE · ${intervalMillis}ms" else "PAUSED",
            style = MaterialTheme.typography.labelMedium,
            color = if (isLive) TextPrimary else TextSecondary,
        )
    }
}

@Composable
private fun HeroCard(reading: BatteryReading?) {
    val connected = reading?.isPowerConnected == true
    val accent = when {
        connected -> EnergyGreen
        reading?.chargeState == ChargeState.DISCHARGING -> EnergyCyan
        else -> TextSecondary
    }
    val gradient = Brush.linearGradient(
        colors = listOf(
            CardBackgroundElevated,
            if (connected) Color(0xFF0D2B20) else Color(0xFF10231F),
            CardBackground,
        ),
        start = Offset.Zero,
        end = Offset(1_100f, 900f),
    )
    val animatedPower = animatedMetric(reading?.powerMagnitudeWatts, "hero power")
    val animatedVoltage = animatedMetric(reading?.voltageVolts, "hero voltage")
    val animatedCurrent = animatedMetric(reading?.currentAmps, "hero current")
    val animatedLevel = animatedInteger(reading?.levelPercent, "hero battery")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(gradient)
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(30.dp))
            .padding(22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(
                text = reading?.chargeState?.label ?: "Reading battery",
                accent = accent,
            )
            Text(
                text = reading?.powerSource?.label ?: "Detecting source",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
            )
        }

        Spacer(Modifier.height(26.dp))
        PowerFlowDiagram(reading = reading, accent = accent)
        Spacer(Modifier.height(24.dp))

        Text(
            text = powerDirectionLabel(reading),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = animatedPower.formatPowerNumber(),
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "W",
                modifier = Modifier.padding(bottom = 7.dp),
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
            )
        }
        Text(
            text = "Net battery power",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroMetric(
                label = "VOLTAGE",
                value = animatedVoltage.formatValue(2, "V"),
                modifier = Modifier.weight(1f),
            )
            HeroMetric(
                label = "CURRENT",
                value = animatedCurrent.formatSignedValue(2, "A"),
                modifier = Modifier.weight(1f),
            )
            HeroMetric(
                label = "BATTERY",
                value = animatedLevel?.let { "$it%" } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(11.dp))
        ChargingProfileStrip(reading = reading, accent = accent)
    }
}

@Composable
private fun ChargingProfileStrip(
    reading: BatteryReading?,
    accent: Color,
) {
    val profile = reading?.chargingProfile
    val active = reading?.isPowerConnected == true
    val animatedCeiling = animatedMetric(
        target = reading?.sourceCeilingWatts?.takeIf { active },
        label = "source ceiling",
        durationMillis = 260,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "P",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "CHARGING PROFILE",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
                Text(
                    text = if (active) profile?.label ?: "System managed" else "Not active",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                if (active && profile != null) {
                    Text(
                        text = profile.description,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }
            }
        }
        if (active && animatedCeiling != null) {
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = animatedCeiling.formatValue(1, "W"),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
                Text(
                    text = "SOURCE CEILING",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(Modifier.width(7.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = accent)
    }
}

@Composable
private fun PowerFlowDiagram(
    reading: BatteryReading?,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceNode(source = reading?.powerSource, accent = accent)
        AnimatedFlowLine(
            active = reading?.isPowerConnected == true,
            color = accent,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        BatteryNode(levelPercent = reading?.levelPercent, accent = accent)
    }
}

@Composable
private fun SourceNode(source: PowerSource?, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.10f))
                .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SourceGlyph(source = source, color = accent)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = when (source) {
                PowerSource.WIRELESS -> "PAD"
                PowerSource.AC, PowerSource.USB -> "CABLE"
                PowerSource.DOCK -> "DOCK"
                PowerSource.NONE -> "DEVICE"
                else -> "SOURCE"
            },
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SourceGlyph(source: PowerSource?, color: Color) {
    Canvas(modifier = Modifier.size(27.dp)) {
        val stroke = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round)
        when (source) {
            PowerSource.WIRELESS -> {
                drawCircle(color, radius = 2.5.dp.toPx(), center = center)
                drawArc(
                    color = color,
                    startAngle = 210f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(5.dp.toPx(), 5.dp.toPx()),
                    size = Size(17.dp.toPx(), 17.dp.toPx()),
                    style = stroke,
                )
                drawArc(
                    color = color.copy(alpha = 0.6f),
                    startAngle = 210f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = size,
                    style = stroke,
                )
            }

            PowerSource.NONE, null -> {
                drawCircle(color.copy(alpha = 0.65f), style = stroke)
                drawLine(
                    color = color,
                    start = Offset(7.dp.toPx(), center.y),
                    end = Offset(20.dp.toPx(), center.y),
                    strokeWidth = 2.3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            else -> {
                val bolt = Path().apply {
                    moveTo(15.dp.toPx(), 1.dp.toPx())
                    lineTo(6.dp.toPx(), 15.dp.toPx())
                    lineTo(13.dp.toPx(), 15.dp.toPx())
                    lineTo(10.dp.toPx(), 26.dp.toPx())
                    lineTo(21.dp.toPx(), 11.dp.toPx())
                    lineTo(14.dp.toPx(), 11.dp.toPx())
                    close()
                }
                drawPath(bolt, color)
            }
        }
    }
}

@Composable
private fun AnimatedFlowLine(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "power flow")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "flow position",
    )
    Canvas(modifier = modifier.height(44.dp)) {
        drawLine(
            color = color.copy(alpha = if (active) 0.28f else 0.10f),
            start = Offset(0f, center.y),
            end = Offset(size.width, center.y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        if (active) {
            repeat(3) { index ->
                val dotProgress = (progress + index / 3f) % 1f
                val dotCenter = Offset(size.width * dotProgress, center.y)
                drawCircle(
                    color = color.copy(alpha = 0.14f),
                    radius = 7.dp.toPx(),
                    center = dotCenter,
                )
                drawCircle(color = color, radius = 2.7.dp.toPx(), center = dotCenter)
            }
        }
    }
}

@Composable
private fun BatteryNode(levelPercent: Int?, accent: Color) {
    val animatedLevel = animatedInteger(
        target = levelPercent,
        label = "battery fill",
        durationMillis = 350,
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(58.dp)
                .border(2.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(9.dp))
                .padding(5.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((42.dp * ((animatedLevel ?: 0) / 100f)).coerceAtLeast(2.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent),
            )
            Text(
                text = animatedLevel?.let { "$it" } ?: "—",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelLarge,
                color = if ((animatedLevel ?: 0) > 55) AppBackground else TextPrimary,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = "BATTERY",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Color.Black.copy(alpha = 0.16f))
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(3.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
}

@Composable
private fun LivePowerCard(
    reading: BatteryReading?,
    points: List<PowerPoint>,
) {
    val accent = if (reading?.isPowerConnected == true) EnergyGreen else EnergyCyan
    val peak = points.maxOfOrNull { abs(it.watts) }
    val animatedPeak = animatedMetric(peak, "chart peak")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, DividerGreen),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "LIVE POWER",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                    Text(
                        text = "Last 30 seconds",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = animatedPeak.formatValue(1, "W"),
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                    )
                    Text(
                        text = "PEAK",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PowerChart(
                points = points,
                color = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp),
            )
        }
    }
}

@Composable
private fun PowerChart(
    points: List<PowerPoint>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        repeat(4) { row ->
            val y = size.height * row / 3f
            drawLine(
                color = DividerGreen.copy(alpha = 0.55f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        if (points.size < 2) {
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(0f, size.height * 0.68f),
                end = Offset(size.width, size.height * 0.68f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val values = points.map { it.watts }
        val minimum = min(values.minOrNull() ?: 0.0, 0.0)
        val maximum = max(values.maxOrNull() ?: 0.0, 0.0)
        val paddedRange = max(maximum - minimum, 0.5)
        val chartMin = minimum - paddedRange * 0.10
        val chartMax = maximum + paddedRange * 0.10
        val range = chartMax - chartMin

        fun xFor(index: Int): Float = size.width * index / (points.size - 1).toFloat()
        fun yFor(value: Double): Float =
            (size.height * (1.0 - ((value - chartMin) / range))).toFloat()

        val linePath = Path()
        val fillPath = Path()
        points.forEachIndexed { index, point ->
            val x = xFor(index)
            val y = yFor(point.watts)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        val zeroY = yFor(0.0)
        if (zeroY in 0f..size.height) {
            drawLine(
                color = TextSecondary.copy(alpha = 0.20f),
                start = Offset(0f, zeroY),
                end = Offset(size.width, zeroY),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.24f), Color.Transparent),
            ),
        )
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = color,
            radius = 3.5.dp.toPx(),
            center = Offset(size.width, yFor(points.last().watts)),
        )
    }
}

@Composable
private fun MetricsGrid(reading: BatteryReading?) {
    val animatedCurrent = animatedMetric(reading?.currentAmps, "current now")
    val animatedAverage = animatedMetric(
        reading?.averageCurrentMicroamps?.div(1_000_000.0),
        "average current",
    )
    val animatedVoltage = animatedMetric(reading?.voltageVolts, "metric voltage")
    val animatedTemperature = animatedMetric(reading?.temperatureCelsius, "temperature")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("LIVE METRICS")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                label = "Current now",
                value = animatedCurrent.formatSignedValue(3, "A"),
                hint = "Instantaneous",
                accent = EnergyGreen,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                label = "Average current",
                value = animatedAverage.formatSignedValue(3, "A"),
                hint = "Device average",
                accent = EnergyCyan,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                label = "Voltage",
                value = animatedVoltage.formatValue(3, "V"),
                hint = "Battery terminal",
                accent = WarningAmber,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                label = "Temperature",
                value = animatedTemperature.formatValue(1, "°C"),
                hint = "Battery sensor",
                accent = Color(0xFFFF8FA3),
                modifier = Modifier.weight(1f),
            )
        }
        ThermalHeadroomCard(reading = reading)
    }
}

@Composable
private fun ThermalHeadroomCard(reading: BatteryReading?) {
    val animatedHeadroom = animatedMetric(
        target = reading?.thermalHeadroomForecast30Seconds,
        label = "thermal headroom",
        durationMillis = 450,
    )
    val fraction = (animatedHeadroom ?: 0.0).toFloat().coerceIn(0f, 1f)
    val accent = when {
        fraction >= 1f -> Color(0xFFFF8FA3)
        fraction >= 0.75f -> WarningAmber
        else -> EnergyCyan
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBackground)
            .border(1.dp, DividerGreen, RoundedCornerShape(20.dp))
            .padding(15.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "THERMAL HEADROOM · 30S FORECAST",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
                Text(
                    text = animatedHeadroom?.let {
                        "${(it * 100.0).toInt()}% of severe threshold"
                    } ?: "Forecast unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            }
            Text(
                text = reading?.thermalStatus?.label ?: "—",
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
        }
        Spacer(Modifier.height(11.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(50))
                .background(DividerGreen),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(7.dp)
                    .background(accent),
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    hint: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(121.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardBackground)
            .border(1.dp, DividerGreen, RoundedCornerShape(20.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(7.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(text = hint, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun BatteryDetails(reading: BatteryReading?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("BATTERY DETAILS")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = CardBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, DividerGreen),
        ) {
            Column(modifier = Modifier.padding(horizontal = 17.dp, vertical = 5.dp)) {
                DetailRow(
                    label = "State",
                    value = reading?.chargeState?.label ?: "—",
                )
                DetailDivider()
                DetailRow(
                    label = "Source",
                    value = reading?.powerSource?.label ?: "—",
                )
                DetailDivider()
                DetailRow(
                    label = "Charging profile",
                    value = if (reading?.isPowerConnected == true) {
                        reading.chargingProfile?.label ?: "System managed"
                    } else {
                        "Not active"
                    },
                )
                DetailDivider()
                DetailRow(
                    label = "Health",
                    value = reading?.health?.label ?: "—",
                )
                DetailDivider()
                DetailRow(
                    label = "Thermal status",
                    value = reading?.thermalStatus?.label ?: "—",
                )
                DetailDivider()
                DetailRow(
                    label = "Time to full",
                    value = reading?.chargeTimeRemainingMillis.formatDuration(),
                )
                DetailDivider()
                DetailRow(
                    label = "Remaining charge",
                    value = reading?.chargeCounterMilliampHours.formatValue(0, "mAh"),
                )
                DetailDivider()
                DetailRow(
                    label = "Remaining energy",
                    value = reading?.energyCounterWattHours.formatValue(2, "Wh"),
                )
                DetailDivider()
                DetailRow(
                    label = "Cycle count",
                    value = reading?.cycleCount?.toString() ?: "Unavailable",
                )
                DetailDivider()
                DetailRow(
                    label = "Chemistry",
                    value = reading?.technology ?: "Unavailable",
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun DetailDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerGreen.copy(alpha = 0.7f)),
    )
}

@Composable
private fun MeasurementNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EnergyGreenMuted.copy(alpha = 0.42f))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "i",
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(EnergyGreen.copy(alpha = 0.16f))
                .padding(top = 2.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = EnergyGreen,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Power is a battery-side estimate from voltage × net current. " +
                "Android does not expose the charger's negotiated input wattage.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

private fun powerDirectionLabel(reading: BatteryReading?): String {
    val powerWatts = reading?.powerWatts ?: return "POWER UNAVAILABLE"
    return when {
        powerWatts > 0.01 -> "FLOWING TO BATTERY"
        powerWatts < -0.01 -> "FLOWING FROM BATTERY"
        reading.chargeState == ChargeState.FULL -> "BATTERY FULL"
        else -> "POWER IDLE"
    }
}

private fun Double?.formatPowerNumber(): String = when {
    this == null || !isFinite() -> "—"
    this < 10.0 -> String.format(Locale.US, "%.2f", this)
    this < 100.0 -> String.format(Locale.US, "%.1f", this)
    else -> String.format(Locale.US, "%.0f", this)
}

private fun Double?.formatValue(decimals: Int, unit: String): String {
    if (this == null || !isFinite()) return "—"
    return String.format(Locale.US, "%.${decimals}f %s", this, unit)
}

private fun Double?.formatSignedValue(decimals: Int, unit: String): String {
    if (this == null || !isFinite()) return "—"
    return String.format(Locale.US, "%+.${decimals}f %s", this, unit)
}

private fun Long?.formatDuration(): String {
    if (this == null || this < 0L) return "Unavailable"
    val totalMinutes = this / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

@Composable
private fun animatedMetric(
    target: Double?,
    label: String,
    durationMillis: Int = 160,
): Double? {
    val animated by animateFloatAsState(
        targetValue = target?.toFloat() ?: 0f,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = LinearOutSlowInEasing,
        ),
        label = label,
    )
    return if (target == null) null else animated.toDouble()
}

@Composable
private fun animatedInteger(
    target: Int?,
    label: String,
    durationMillis: Int = 220,
): Int? {
    val animated by animateIntAsState(
        targetValue = target ?: 0,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = LinearOutSlowInEasing,
        ),
        label = label,
    )
    return if (target == null) null else animated
}
