package com.clipsaver.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val BgTop = Color(0xFF0E0B22)
val BgBottom = Color(0xFF241A5E)
val Violet = Color(0xFF7C4DFF)
val Pink = Color(0xFFFF4D8D)
val Cyan = Color(0xFF22D3EE)
val Green = Color(0xFF34D399)
val Red = Color(0xFFFF6B6B)
val TextDim = Color(0xFFB8B3D6)

val AccentBrush = Brush.horizontalGradient(listOf(Violet, Pink))

private val Scheme = darkColorScheme(
    primary = Violet,
    secondary = Pink,
    background = BgTop,
    surface = BgTop,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun ClipSaverTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
