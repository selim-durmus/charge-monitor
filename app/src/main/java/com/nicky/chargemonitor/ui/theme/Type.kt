package com.nicky.chargemonitor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

// Tabular figures keep live numbers from shifting width as digits change four times a second.
private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

val ChargeTypography = Typography(
    displayLarge = Base.displayLarge.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 60.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1.5).sp,
    ).tabular(),
    displayMedium = Base.displayMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1).sp,
    ).tabular(),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold).tabular(),
    headlineLarge = Base.headlineLarge.tabular(),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold).tabular(),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold).tabular(),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold).tabular(),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold).tabular(),
    titleSmall = Base.titleSmall.tabular(),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge.tabular(),
    labelMedium = Base.labelMedium.tabular(),
    labelSmall = Base.labelSmall.tabular(),
)
