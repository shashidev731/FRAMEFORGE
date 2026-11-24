package com.frameforge.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Premium Dark Palette (Default)
val CanvasDark = Color(0xFF0F1115)
val SurfaceDark = Color(0xFF171A21)
val SurfaceElevatedDark = Color(0xFF1F232D)
val CardBorderDark = Color(0xFF2B303C)
val TextPrimaryDark = Color(0xFFF3F4F6)
val TextMutedDark = Color(0xFF9CA3AF)
val AccentViolet = Color(0xFF6366F1)
val AccentVioletSoft = Color(0xFF282A45)
val AccentCyan = Color(0xFF06B6D4)
val AccentSuccess = Color(0xFF10B981)
val AccentWarning = Color(0xFFF59E0B)
val AccentError = Color(0xFFEF4444)

// Classic Light Palette (Optional)
val CanvasLight = Color(0xFFF6F7F9)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceElevatedLight = Color(0xFFF0F2F6)
val CardBorderLight = Color(0xFFE2E5EC)
val TextPrimaryLight = Color(0xFF111827)
val TextMutedLight = Color(0xFF6B7280)
val AccentLightSoft = Color(0xFFEEF0FF)

// Semantic dynamic variables mapped to active palette
var Canvas = CanvasDark
var SurfaceWhite = SurfaceDark
var SurfaceElevated = SurfaceElevatedDark
var Ink = TextPrimaryDark
var Muted = TextMutedDark
var Line = CardBorderDark
var Accent = AccentViolet
var AccentSoft = AccentVioletSoft

private val DarkColors = darkColorScheme(
    primary = AccentViolet,
    onPrimary = Color.White,
    primaryContainer = AccentVioletSoft,
    onPrimaryContainer = Color(0xFFC7D2FE),
    secondary = AccentCyan,
    onSecondary = Color.Black,
    background = CanvasDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceElevatedDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextMutedDark,
    outline = CardBorderDark,
    error = AccentError
)

private val LightColors = lightColorScheme(
    primary = AccentViolet,
    onPrimary = Color.White,
    primaryContainer = AccentLightSoft,
    onPrimaryContainer = AccentViolet,
    secondary = AccentCyan,
    onSecondary = Color.Black,
    background = CanvasLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceElevatedLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextMutedLight,
    outline = CardBorderLight,
    error = AccentError
)

@Composable
fun FrameforgeTheme(
    darkTheme: Boolean = true, // Default to true for the requested premium dark theme
    content: @Composable () -> Unit
) {
    if (darkTheme) {
        Canvas = CanvasDark
        SurfaceWhite = SurfaceDark
        SurfaceElevated = SurfaceElevatedDark
        Ink = TextPrimaryDark
        Muted = TextMutedDark
        Line = CardBorderDark
        Accent = AccentViolet
        AccentSoft = AccentVioletSoft
    } else {
        Canvas = CanvasLight
        SurfaceWhite = SurfaceLight
        SurfaceElevated = SurfaceElevatedLight
        Ink = TextPrimaryLight
        Muted = TextMutedLight
        Line = CardBorderLight
        Accent = AccentViolet
        AccentSoft = AccentLightSoft
    }

    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
