package com.nicky.chargemonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AppBackground = Color(0xFF06100D)
val CardBackground = Color(0xFF0C1B16)
val CardBackgroundElevated = Color(0xFF12261F)
val EnergyGreen = Color(0xFF50F5A9)
val EnergyGreenMuted = Color(0xFF193D31)
val EnergyCyan = Color(0xFF51D9F5)
val WarningAmber = Color(0xFFFFC66D)
val TextPrimary = Color(0xFFF3FFF9)
val TextSecondary = Color(0xFF93AA9F)
val DividerGreen = Color(0xFF20352D)

private val ChargeColorScheme = darkColorScheme(
    primary = EnergyGreen,
    onPrimary = Color(0xFF002114),
    secondary = EnergyCyan,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = CardBackgroundElevated,
    onSurfaceVariant = TextSecondary,
    outline = DividerGreen,
    error = Color(0xFFFF8A80),
)

@Composable
fun ChargeMonitorTheme(content: @Composable () -> Unit) {
    // The dashboard deliberately uses a dark energy-console aesthetic in either
    // system theme. Keeping this stable also improves chart contrast while charging.
    MaterialTheme(
        colorScheme = ChargeColorScheme,
        typography = ChargeTypography,
        content = content,
    )
}
