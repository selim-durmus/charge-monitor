package com.nicky.chargemonitor.ui.sessions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nicky.chargemonitor.R
import com.nicky.chargemonitor.session.ChargingSessionSummary
import com.nicky.chargemonitor.session.MAX_STORED_SESSIONS
import com.nicky.chargemonitor.ui.components.AppIcon
import com.nicky.chargemonitor.ui.components.CardShape
import com.nicky.chargemonitor.ui.components.EmptyState
import com.nicky.chargemonitor.ui.components.FootNote
import com.nicky.chargemonitor.ui.components.Pill
import com.nicky.chargemonitor.ui.components.ScreenScaffold
import com.nicky.chargemonitor.ui.components.SectionCard
import com.nicky.chargemonitor.ui.components.color
import com.nicky.chargemonitor.ui.components.formatDuration
import com.nicky.chargemonitor.ui.components.formatSessionTime
import com.nicky.chargemonitor.ui.components.formatUnit
import com.nicky.chargemonitor.ui.components.levelRange
import com.nicky.chargemonitor.ui.components.screenContent
import com.nicky.chargemonitor.ui.components.shortLabel
import com.nicky.chargemonitor.ui.theme.ChargeTheme

@Composable
fun SessionsScreen(
    summaries: List<ChargingSessionSummary>,
    activeSessionId: String?,
    loaded: Boolean,
    onOpenSession: (String) -> Unit,
    onCompare: (List<String>) -> Unit,
    onStartGuidedTest: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    fun exitSelection() {
        selecting = false
        selected = emptyList()
    }
    BackHandler(enabled = selecting, onBack = ::exitSelection)

    ScreenScaffold(
        title = if (selecting) "Pick two to compare" else "Sessions",
        subtitle = if (selecting) "${selected.size} of 2 selected" else null,
        onBack = if (selecting) ::exitSelection else null,
        actions = {
            if (selecting) {
                TextButton(
                    onClick = {
                        onCompare(selected)
                        exitSelection()
                    },
                    enabled = selected.size == 2,
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text("Compare") }
            }
        },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            contentPadding = padding.screenContent(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!selecting) {
                item(key = "tools") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton(
                            icon = R.drawable.ic_compare,
                            label = "Compare",
                            enabled = summaries.size >= 2,
                            onClick = { selecting = true },
                            modifier = Modifier.weight(1f),
                        )
                        ToolButton(
                            icon = R.drawable.ic_science,
                            label = "A/B charger test",
                            enabled = true,
                            onClick = onStartGuidedTest,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (loaded && summaries.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = R.drawable.ic_history,
                        title = "No sessions yet",
                        body = "Plug in a charger with this app open. Each charge is recorded " +
                            "automatically and analysed when it ends.",
                    )
                }
            }
            items(summaries, key = { it.id }) { summary ->
                val selectionIndex = selected.indexOf(summary.id)
                SessionRow(
                    summary = summary,
                    isRecording = summary.id == activeSessionId,
                    selectionLabel = when {
                        !selecting -> null
                        selectionIndex == 0 -> "A"
                        selectionIndex == 1 -> "B"
                        else -> ""
                    },
                    onClick = {
                        if (!selecting) {
                            onOpenSession(summary.id)
                        } else if (selectionIndex >= 0) {
                            selected = selected - summary.id
                        } else if (selected.size < 2) {
                            selected = selected + summary.id
                        }
                    },
                )
            }
            if (summaries.isNotEmpty()) {
                item(key = "footer") {
                    FootNote(
                        "Your $MAX_STORED_SESSIONS most recent sessions are kept. Recording runs " +
                            "while this app is open, or in the background with screen-off recording.",
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        AppIcon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One line of identity, one line of numbers, one line of verdict.
 * [selectionLabel] is null outside selection mode, "" when unselected, or "A"/"B".
 */
@Composable
private fun SessionRow(
    summary: ChargingSessionSummary,
    isRecording: Boolean,
    selectionLabel: String?,
    onClick: () -> Unit,
) {
    val tone = summary.diagnosis.tone.color()
    val selectedColor = when (selectionLabel) {
        "A" -> ChargeTheme.colors.seriesA
        "B" -> ChargeTheme.colors.seriesB
        else -> null
    }
    SectionCard(
        onClick = onClick,
        modifier = if (selectedColor != null) {
            Modifier.border(2.dp, selectedColor, CardShape)
        } else {
            Modifier
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionLabel != null) {
                SelectionBadge(selectionLabel, selectedColor)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = summary.startedAtMillis.formatSessionTime(),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${summary.accessoryName ?: summary.source.shortLabel} · " +
                        summary.durationMillis.formatDuration(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isRecording) {
                Pill("Recording", color = MaterialTheme.colorScheme.error, showDot = true)
            } else {
                Text(
                    text = levelRange(summary.startLevelPercent, summary.endLevelPercent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            InlineStat("Average", summary.averagePowerWatts.formatUnit(1, "W"))
            InlineStat("Peak", summary.maximumPowerWatts.formatUnit(1, "W"))
            InlineStat("Max temp", summary.peakTemperatureCelsius.formatUnit(1, "°C"))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(tone, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = summary.diagnosis.title,
                style = MaterialTheme.typography.bodyMedium,
                color = tone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SelectionBadge(label: String, color: androidx.compose.ui.graphics.Color?) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .then(
                if (color != null) {
                    Modifier.background(color, CircleShape)
                } else {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (color != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.surface)
        }
    }
}

@Composable
private fun InlineStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.titleSmall)
    }
}
