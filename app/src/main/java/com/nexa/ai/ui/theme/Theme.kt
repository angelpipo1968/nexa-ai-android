package com.nexa.ai.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Futuristic Accent: Cyan-Blue gradient feel ──
val NexaAccent = Color(0xFF00D4AA)       // Primary teal-cyan
val NexaAccentLight = Color(0xFF33FFD0)  // Lighter variant
val NexaAccentDark = Color(0xFF00A882)   // Darker variant
val NexaGlow = Color(0xFF00D4AA)         // Glow color

private val DarkColorScheme = darkColorScheme(
    primary = NexaAccent,
    onPrimary = Color.Black,
    background = Color(0xFF050508),        // Deeper black with blue tint
    surface = Color(0xFF0D0D12),           // Subtle surface
    surfaceVariant = Color(0xFF161620),    // Card/container bg
    surfaceContainerLow = Color(0xFF0A0A0F),
    surfaceContainer = Color(0xFF121218),
    surfaceContainerHigh = Color(0xFF1A1A24),
    onBackground = Color(0xFFE8E8EC),     // Slightly cool white
    onSurface = Color(0xFFE8E8EC),
    onSurfaceVariant = Color(0xFF6B6B80), // Muted cool gray
    outline = Color(0xFF1E1E2A),          // Subtle borders
    outlineVariant = Color(0xFF2A2A38),
    error = Color(0xFFFF4D6A),            // Soft red
    inverseSurface = Color(0xFFE8E8EC),
)

private val LightColorScheme = lightColorScheme(
    primary = NexaAccentDark,
    onPrimary = Color.White,
    background = Color(0xFFF8F9FC),       // Cool white
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F1F5),
    surfaceContainerLow = Color(0xFFFAFBFE),
    surfaceContainer = Color(0xFFF5F6FA),
    surfaceContainerHigh = Color(0xFFECEEF4),
    onBackground = Color(0xFF0A0A12),
    onSurface = Color(0xFF0A0A12),
    onSurfaceVariant = Color(0xFF5A5A70),
    outline = Color(0xFFE0E2EA),
    outlineVariant = Color(0xFFD0D2DA),
    error = Color(0xFFE53E5A),
    inverseSurface = Color(0xFF0A0A12),
)

@Composable
fun NexaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
