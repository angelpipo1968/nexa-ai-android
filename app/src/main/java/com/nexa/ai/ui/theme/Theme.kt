package com.nexa.ai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Emerald accent (default from web)
val NexaAccent = Color(0xFF00E5A0)
val NexaAccentDark = Color(0xFF00C98B)
val NexaAccentGlow = Color(0x4D00E5A0)

private val DarkColorScheme = darkColorScheme(
    primary = NexaAccent,
    onPrimary = Color.Black,
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF141414),
    surfaceVariant = Color(0xFF1F1F1F),
    onBackground = Color(0xFFF0F0F0),
    onSurface = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF888888),
    outline = Color(0xFF1F1F1F),
    error = Color(0xFFEF4444),
)

private val LightColorScheme = lightColorScheme(
    primary = NexaAccent,
    onPrimary = Color.Black,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF8F9FA),
    surfaceVariant = Color(0xFFE5E7EB),
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE5E7EB),
    error = Color(0xFFEF4444),
)

@Composable
fun NexaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
