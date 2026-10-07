package com.mygithub.lab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ===== NORD 极客深色主题（komi-store 血统）=====
object NordColors {
    // Dark Neutral (komi-store ClassicPersonality 暗黑风)
    val PolarNight0 = Color(0xFF111316)  // background
    val PolarNight1 = Color(0xFF1A1C1E)  // surface
    val PolarNight2 = Color(0xFF1E2023)  // surfaceContainer
    val PolarNight3 = Color(0xFF282A2E)  // surfaceContainerHigh
    val SnowStorm0 = Color(0xFF44474E)   // outlineVariant
    val SnowStorm1 = Color(0xFF8E9099)   // outline
    val SnowStorm2 = Color(0xFFC4C6CF)   // onSurfaceVariant
    val SnowStorm3 = Color(0xFFE3E2E6)   // onBackground/onSurface
    // Accent: COBALT (komi-store 默认强调色)
    val Frost0 = Color(0xFFB6C4FF)       // primary
    val Frost1 = Color(0xFF2C44A8)       // primaryContainer
    val Frost2 = Color(0xFFDCE1FF)       // onPrimaryContainer
    // 状态色
    val AuroraRed = Color(0xFFFFB4AB)    // error
    val AuroraGreen = Color(0xFF4CAF50)
    val AuroraBlue = Color(0xFF2196F3)
    val AuroraYellow = Color(0xFFF4BE48)
    // 兼容旧引用
    val Frost1Old = Color(0xFF88C0D0)
    val Frost2Old = Color(0xFF81A1C1)
    val Frost0Old = Color(0xFF8FBCBB)
}

val NordDarkColorScheme = darkColorScheme(
    primary = NordColors.Frost0,           // #B6C4FF Cobalt
    onPrimary = Color(0xFF00257A),
    primaryContainer = NordColors.Frost1,  // #2C44A8
    onPrimaryContainer = NordColors.Frost2, // #DCE1FF
    secondary = NordColors.Frost0,
    onSecondary = Color(0xFF00257A),
    secondaryContainer = NordColors.Frost1,
    onSecondaryContainer = NordColors.Frost2,
    tertiary = NordColors.Frost0,
    onTertiary = Color(0xFF00257A),
    background = NordColors.PolarNight0,   // #111316
    onBackground = NordColors.SnowStorm3,  // #E3E2E6
    surface = NordColors.PolarNight1,      // #1A1C1E
    onSurface = NordColors.SnowStorm3,
    surfaceVariant = Color(0xFF2A2D33),
    onSurfaceVariant = NordColors.SnowStorm2, // #C4C6CF
    surfaceContainer = NordColors.PolarNight2, // #1E2023
    surfaceContainerHigh = NordColors.PolarNight3, // #282A2E
    outline = NordColors.SnowStorm1,      // #8E9099
    outlineVariant = NordColors.SnowStorm0, // #44474E
    error = NordColors.AuroraRed,         // #FFB4AB
    onError = Color(0xFF690005)
)

// komi-store ClassicPersonality 亮色主题
val NordLightColorScheme = lightColorScheme(
    primary = Color(0xFF3B5BDB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF00164E),
    secondary = Color(0xFF3B5BDB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE1FF),
    onSecondaryContainer = Color(0xFF00164E),
    tertiary = Color(0xFF3B5BDB),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBFBFD),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEEF0F4),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainer = Color(0xFFF3F4F8),
    surfaceContainerHigh = Color(0xFFECEEF3),
    outline = Color(0xFFC4C6CF),
    outlineVariant = Color(0xFFE2E2E6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun MyGitHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NordDarkColorScheme else NordLightColorScheme,
        typography = NordTypography,
        content = content
    )
}
