package com.devnet.adbcompanion.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

internal class DevNetPalette(
    val Background: Color,
    val SurfaceCard: Color,
    val SurfaceElevated: Color,
    val BorderSubtle: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextMuted: Color,
    val Emerald: Color,
    val Cyan: Color,
    val Amber: Color,
    val Red: Color
) {
    val EmeraldDim = Emerald.copy(alpha = 0.12f)
    val CyanDim = Cyan.copy(alpha = 0.12f)
    val AmberDim = Amber.copy(alpha = 0.12f)
    val RedDim = Red.copy(alpha = 0.13f)
}

private val DarkPalette = DevNetPalette(
    Background = Color(0xFF0A0E14),
    SurfaceCard = Color(0xFF121923),
    SurfaceElevated = Color(0xFF1A2432),
    BorderSubtle = Color(0xFF263344),
    TextPrimary = Color(0xFFF1F5F9),
    TextSecondary = Color(0xFF94A3B8),
    TextMuted = Color(0xFF8796AC),
    Emerald = Color(0xFF00E676),
    Cyan = Color(0xFF38BDF8),
    Amber = Color(0xFFFFB300),
    Red = Color(0xFFFF5252)
)

private val LightPalette = DevNetPalette(
    Background = Color(0xFFF3F6FA),
    SurfaceCard = Color.White,
    SurfaceElevated = Color(0xFFE5EBF3),
    BorderSubtle = Color(0xFFC6D1DF),
    TextPrimary = Color(0xFF142033),
    TextSecondary = Color(0xFF46566B),
    TextMuted = Color(0xFF53637A),
    Emerald = Color(0xFF00643A),
    Cyan = Color(0xFF07547C),
    Amber = Color(0xFF824300),
    Red = Color(0xFFA41A26)
)

private val LocalDevNetColors = compositionLocalOf { DarkPalette }

internal val DevNetColors: DevNetPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalDevNetColors.current

private val DevNetDarkScheme = darkColorScheme(
    primary = DarkPalette.Emerald,
    onPrimary = Color(0xFF042211),
    secondary = DarkPalette.Cyan,
    onSecondary = Color(0xFF051C29),
    background = DarkPalette.Background,
    onBackground = DarkPalette.TextPrimary,
    surface = DarkPalette.SurfaceCard,
    onSurface = DarkPalette.TextPrimary,
    surfaceVariant = DarkPalette.SurfaceElevated,
    onSurfaceVariant = DarkPalette.TextSecondary,
    surfaceContainer = DarkPalette.SurfaceElevated,
    error = DarkPalette.Red,
    onError = Color(0xFF270505),
    outline = DarkPalette.TextMuted,
    outlineVariant = DarkPalette.BorderSubtle
)

private val DevNetLightScheme = lightColorScheme(
    primary = LightPalette.Emerald,
    onPrimary = Color.White,
    secondary = LightPalette.Cyan,
    onSecondary = Color.White,
    background = LightPalette.Background,
    onBackground = LightPalette.TextPrimary,
    surface = LightPalette.SurfaceCard,
    onSurface = LightPalette.TextPrimary,
    surfaceVariant = LightPalette.SurfaceElevated,
    onSurfaceVariant = LightPalette.TextSecondary,
    surfaceContainer = LightPalette.SurfaceElevated,
    error = LightPalette.Red,
    onError = Color.White,
    outline = LightPalette.TextMuted,
    outlineVariant = LightPalette.BorderSubtle
)

@Composable
fun DevNetTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkPalette else LightPalette
    val colorScheme = if (darkTheme) DevNetDarkScheme else DevNetLightScheme

    CompositionLocalProvider(LocalDevNetColors provides colors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
