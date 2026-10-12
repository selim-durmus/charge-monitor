package com.nicky.chargemonitor.ui.components

import com.nicky.chargemonitor.battery.ChargingProfile
import com.nicky.chargemonitor.battery.PowerSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val NO_VALUE = "—"

fun Double?.formatNumber(decimals: Int): String =
    if (this == null || !isFinite()) NO_VALUE else String.format(Locale.US, "%.${decimals}f", this)

fun Double?.formatUnit(decimals: Int, unit: String): String =
    if (this == null || !isFinite()) NO_VALUE else "${formatNumber(decimals)} $unit"

fun Double?.formatSigned(decimals: Int, unit: String): String =
    if (this == null || !isFinite()) {
        NO_VALUE
    } else {
        String.format(Locale.US, "%+.${decimals}f %s", this, unit)
    }

/** Fewer decimals as the number grows so the hero reading stays a stable width. */
fun Double?.formatWatts(): String = when {
    this == null || !isFinite() -> NO_VALUE
    this < 10.0 -> formatNumber(2)
    this < 100.0 -> formatNumber(1)
    else -> formatNumber(0)
}

fun Int?.formatPercent(): String = this?.let { "$it%" } ?: NO_VALUE

fun levelRange(start: Int?, end: Int?): String =
    if (start == null && end == null) NO_VALUE else "${start.formatPercent()} → ${end.formatPercent()}"

/** "1h 05m", "12m 30s", "45s". */
fun Long.formatDuration(): String {
    val seconds = coerceAtLeast(0L) / 1_000L
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val remainder = seconds % 60L
    return when {
        hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
        minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, remainder)
        else -> "${remainder}s"
    }
}

/** Short form for "time to full": "1h 05m" or "42m". */
fun Long.formatShortDuration(): String {
    val minutes = coerceAtLeast(0L) / 60_000L
    return if (minutes >= 60) {
        String.format(Locale.US, "%dh %02dm", minutes / 60, minutes % 60)
    } else {
        "${minutes}m"
    }
}

/** Stopwatch style "4:07" or "1:02:07". */
fun Long.formatClock(): String {
    val seconds = coerceAtLeast(0L) / 1_000L
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val remainder = seconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, remainder)
    }
}

/** "Today · 9:41 PM", "Yesterday · 7:02 AM", "Mon, Oct 6 · 9:41 PM". */
fun Long.formatSessionTime(zone: ZoneId = ZoneId.systemDefault()): String {
    val dateTime = Instant.ofEpochMilli(this).atZone(zone)
    val today = LocalDate.now(zone)
    val day = when (dateTime.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DAY_FORMAT.format(dateTime)
    }
    return "$day · ${TIME_FORMAT.format(dateTime)}"
}

val PowerSource.shortLabel: String
    get() = when (this) {
        PowerSource.AC -> "Wired"
        PowerSource.USB -> "USB"
        PowerSource.WIRELESS -> "Wireless"
        PowerSource.DOCK -> "Dock"
        PowerSource.NONE -> "Unplugged"
        PowerSource.UNKNOWN -> "Charger"
    }

/** Null for the standard profile, which is not worth calling out. */
val ChargingProfile?.notableLabel: String?
    get() = when (this) {
        null, ChargingProfile.STANDARD, ChargingProfile.SYSTEM_MANAGED -> null
        ChargingProfile.TEMPERATURE_COLD -> "Cold protection"
        ChargingProfile.TEMPERATURE_HOT -> "Heat protection"
        ChargingProfile.ADAPTIVE -> "Adaptive"
        ChargingProfile.LONG_LIFE -> "80% limit"
    }

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
