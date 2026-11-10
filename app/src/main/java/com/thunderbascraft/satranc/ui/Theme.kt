package com.thunderbascraft.satranc.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/** Fixed chess palette that matches the green board (no dynamic colour). */
private val Light = lightColorScheme(
    primary = Color(0xFF3E5C2B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E8C4),
    onPrimaryContainer = Color(0xFF13200A),
    secondary = Color(0xFF9A7433),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E2C2),
    onSecondaryContainer = Color(0xFF2E2006),
    tertiary = Color(0xFF6B5E3F),
    background = Color(0xFFF7F4EC),
    onBackground = Color(0xFF1C1C17),
    surface = Color(0xFFF7F4EC),
    onSurface = Color(0xFF1C1C17),
    surfaceVariant = Color(0xFFE8E3D6),
    onSurfaceVariant = Color(0xFF48473E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2EEE4),
    surfaceContainer = Color(0xFFEDE8DC),
    surfaceContainerHigh = Color(0xFFE8E3D6),
    surfaceContainerHighest = Color(0xFFE2DDCF),
    outline = Color(0xFF7A786C),
    outlineVariant = Color(0xFFCBC6B8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA7CF84),
    onPrimary = Color(0xFF173009),
    primaryContainer = Color(0xFF2F4721),
    onPrimaryContainer = Color(0xFFD7E8C4),
    secondary = Color(0xFFE0C285),
    onSecondary = Color(0xFF3F2E05),
    secondaryContainer = Color(0xFF574419),
    onSecondaryContainer = Color(0xFFF1E2C2),
    background = Color(0xFF1B1D18),
    onBackground = Color(0xFFE4E3DA),
    surface = Color(0xFF1B1D18),
    onSurface = Color(0xFFE4E3DA),
    surfaceVariant = Color(0xFF2C2F28),
    onSurfaceVariant = Color(0xFFC6C7BB),
    surfaceContainerLowest = Color(0xFF151713),
    surfaceContainerLow = Color(0xFF20231D),
    surfaceContainer = Color(0xFF252822),
    surfaceContainerHigh = Color(0xFF2C2F28),
    surfaceContainerHighest = Color(0xFF363932),
    outline = Color(0xFF909286),
    outlineVariant = Color(0xFF45483F),
)

private val Base = Typography()
private val AppTypography = Base.copy(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Colours used for analysis symbols. */
object QualityColors {
    val Inaccuracy = Color(0xFFD4A514)
    val Mistake = Color(0xFFE5791E)
    val Blunder = Color(0xFFD23C32)
}

@Composable
fun SatrancTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
