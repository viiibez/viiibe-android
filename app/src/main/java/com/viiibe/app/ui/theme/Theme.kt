package com.viiibe.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Viiibe brand colors - vibrant purple/cyan gaming aesthetic
object ViiibeColors {
    val Primary = Color(0xFF7C4DFF)       // Vibrant purple
    val PrimaryDark = Color(0xFF651FFF)   // Deep purple
    val Accent = Color(0xFF00E5FF)        // Cyan
    val Secondary = Color(0xFFFF4081)     // Pink
    val Gold = Color(0xFFFFD700)          // Token/reward color
    val Success = Color(0xFF00E676)       // Win color
    val Error = Color(0xFFFF5252)         // Loss/error color
}

private val DarkColorScheme = darkColorScheme(
    primary = ViiibeColors.Primary,
    onPrimary = Color.White,
    primaryContainer = ViiibeColors.PrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = ViiibeColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC51162),
    onSecondaryContainer = Color.White,
    tertiary = ViiibeColors.Accent,
    onTertiary = Color.Black,
    background = Color(0xFF0A0E14),
    onBackground = Color.White,
    surface = Color(0xFF141B24),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E2832),
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = ViiibeColors.Error,
    onError = Color.White
)

@Composable
fun ViiibeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
