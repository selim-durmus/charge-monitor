package com.nicky.chargemonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colors the Material scheme has no slot for: data series and diagnosis tones. */
@Immutable
data class ChargeColors(
    val charging: Color,
    val discharging: Color,
    val power: Color,
    val temperature: Color,
    val event: Color,
    val good: Color,
    val notice: Color,
    val warning: Color,
    val seriesA: Color,
    val seriesB: Color,
    val chartGrid: Color,
)

private val DarkChargeColors = ChargeColors(
    charging = Color(0xFF4FE3A3),
    discharging = Color(0xFF6CC9EC),
    power = Color(0xFF4FE3A3),
    temperature = Color(0xFFFF8FA3),
    event = Color(0xFFF2BE5C),
    good = Color(0xFF4FE3A3),
    notice = Color(0xFF6CC9EC),
    warning = Color(0xFFF2BE5C),
    seriesA = Color(0xFF4FE3A3),
    seriesB = Color(0xFFB59CFF),
    chartGrid = Color(0xFF26312D),
)

private val LightChargeColors = ChargeColors(
    charging = Color(0xFF0B8A58),
    discharging = Color(0xFF0A7BA6),
    power = Color(0xFF0B8A58),
    temperature = Color(0xFFD03E62),
    event = Color(0xFFB7791F),
    good = Color(0xFF0B8A58),
    notice = Color(0xFF0A7BA6),
    warning = Color(0xFFB7791F),
    seriesA = Color(0xFF0B8A58),
    seriesB = Color(0xFF6B4FD8),
    chartGrid = Color(0xFFE1E7E4),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4FE3A3),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF12402D),
    onPrimaryContainer = Color(0xFFB4F5D5),
    secondary = Color(0xFFB3CCC1),
    onSecondary = Color(0xFF1F352D),
    secondaryContainer = Color(0xFF243A32),
    onSecondaryContainer = Color(0xFFCFE9DD),
    tertiary = Color(0xFFF2BE5C),
    onTertiary = Color(0xFF412D00),
    background = Color(0xFF0B0F0E),
    onBackground = Color(0xFFE3ECE8),
    surface = Color(0xFF0B0F0E),
    onSurface = Color(0xFFE3ECE8),
    surfaceVariant = Color(0xFF1C2421),
    onSurfaceVariant = Color(0xFF9DAEA7),
    surfaceContainerLowest = Color(0xFF070A09),
    surfaceContainerLow = Color(0xFF111715),
    surfaceContainer = Color(0xFF151C19),
    surfaceContainerHigh = Color(0xFF1C2421),
    surfaceContainerHighest = Color(0xFF242D2A),
    outline = Color(0xFF52605A),
    outlineVariant = Color(0xFF26312D),
    error = Color(0xFFFF8A80),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0B8A58),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8F2DD),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF4C635A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6EADF),
    onSecondaryContainer = Color(0xFF092019),
    tertiary = Color(0xFFB7791F),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F8F6),
    onBackground = Color(0xFF111614),
    surface = Color(0xFFF5F8F6),
    onSurface = Color(0xFF111614),
    surfaceVariant = Color(0xFFE6ECE9),
    onSurfaceVariant = Color(0xFF55645E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFCFB),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDF2EF),
    surfaceContainerHighest = Color(0xFFE6ECE9),
    outline = Color(0xFF8A9893),
    outlineVariant = Color(0xFFDCE3E0),
    error = Color(0xFFBA1A1A),
)

private val LocalChargeColors = staticCompositionLocalOf { DarkChargeColors }

object ChargeTheme {
    val colors: ChargeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalChargeColors.current
}

@Composable
fun ChargeMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalChargeColors provides if (darkTheme) DarkChargeColors else LightChargeColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = ChargeTypography,
            content = content,
        )
    }
}
