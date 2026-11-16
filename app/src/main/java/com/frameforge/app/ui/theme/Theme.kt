package com.frameforge.app.ui.theme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
val Canvas = Color(0xFFF6F7F9)
val SurfaceWhite = Color(0xFFFFFFFF)
val Ink = Color(0xFF17191F)
val Muted = Color(0xFF777D89)
val Line = Color(0xFFE7E9EF)
val Accent = Color(0xFF5B62E8)
val AccentSoft = Color(0xFFEEF0FF)
private val FrameforgeColors = lightColorScheme(
    primary = Accent, onPrimary = SurfaceWhite, secondary = Ink,
    background = Canvas, surface = SurfaceWhite, onBackground = Ink, onSurface = Ink
)
@Composable fun FrameforgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FrameforgeColors, content = content)
}
