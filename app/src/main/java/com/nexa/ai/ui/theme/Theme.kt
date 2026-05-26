package com.nexa.ai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.nexa.ai.viewmodel.ThemeMode

// ── Futuristic Accent: Emerald-Teal (Custom themes) ──
val NexaAccent = Color(0xFF00F5A0)       // Primary neon green
val NexaAccentLight = Color(0xFF66FFD0)  // Lighter variant
val NexaAccentDark = Color(0xFF00C896)   // Darker variant
val NexaGlow = Color(0xFF00F5A0)         // Glow color

/** CompositionLocal providing the effective accent color globally. */
val LocalAccentColor = compositionLocalOf { NexaAccent }

/** User message bubble color — must be opaque enough for white text readability. */
val NexaUserBubbleLight = Color(0xFF00875A)   // Darker green for light theme user bubbles
val NexaUserBubbleDark = NexaAccent.copy(alpha = 0.12f)  // Subtle accent for dark theme

// ═══════════════════════════════════════
//  DYNAMIC COLOR HELPERS (Material You)
// ═══════════════════════════════════════

/** Returns true if the device supports Material You dynamic colors (Android 12+). */
fun supportsDynamicColors(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Extracts the primary color from the current dynamic color scheme.
 * Used to tint user message bubbles when in SYSTEM mode.
 */
@Composable
fun dynamicPrimaryColor(): Color {
    val context = LocalContext.current
    return if (supportsDynamicColors()) {
        val scheme = if (isSystemInDarkTheme()) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        scheme.primary
    } else {
        NexaAccent
    }
}

// ═══════════════════════════════════════
//  RESPONSIVE HELPERS — Now in AdaptiveLayout.kt
// ═══════════════════════════════════════
// Use: rememberAdaptiveInfo(), adaptive(), adaptiveText(), NexaSpacing, NexaTypographyScale
// Import from com.nexa.ai.ui package

// ═══════════════════════════════════════
//  THEME COMPOSABLE
// ═══════════════════════════════════════

@Composable
fun NexaTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentColor: Color = NexaAccent,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark
    }

    val colorScheme = when (themeMode) {
        // ── Custom neon themes: use our palettes with dynamic accent ──
        ThemeMode.DARK -> darkColorScheme(
            primary = accentColor,
            onPrimary = Color.Black,
            secondary = accentColor.copy(alpha = 0.8f),
            tertiary = accentColor.copy(alpha = 0.6f),
            background = Color(0xFF0A0A0F),
            surface = Color(0xFF12121A),
            surfaceVariant = Color(0xFF1A1A26),
            surfaceContainerLow = Color(0xFF0A0A0F),
            surfaceContainer = Color(0xFF12121A),
            surfaceContainerHigh = Color(0xFF1A1A26),
            onBackground = Color(0xFFE8E8EE),
            onSurface = Color(0xFFE8E8EE),
            onSurfaceVariant = Color(0xFF6B6B7B),
            outline = Color(0xFF1E1E2E),
            outlineVariant = Color(0xFF2A2A3A),
            error = Color(0xFFFF4466),
            inverseSurface = Color(0xFFE8E8EE),
        )
        ThemeMode.LIGHT -> lightColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            secondary = accentColor.copy(alpha = 0.8f),
            tertiary = accentColor.copy(alpha = 0.6f),
            background = Color(0xFFF5F6F8),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE8EAED),
            surfaceContainerLow = Color(0xFFF8F9FB),
            surfaceContainer = Color(0xFFF1F3F5),
            surfaceContainerHigh = Color(0xFFE9ECEF),
            onBackground = Color(0xFF111111),
            onSurface = Color(0xFF111111),
            onSurfaceVariant = Color(0xFF333333),
            outline = Color(0xFF666666),
            outlineVariant = Color(0xFFBBBBBB),
            error = Color(0xFFBA1A1A),
            inverseSurface = Color(0xFF2F3033),
        )
        // ── System: use Material You dynamic colors if available, else fallback ──
        ThemeMode.SYSTEM -> {
            if (supportsDynamicColors()) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) darkColorScheme(
                    primary = accentColor,
                    onPrimary = Color.Black,
                    background = Color(0xFF0A0A0F),
                    surface = Color(0xFF12121A),
                    onBackground = Color(0xFFE8E8EE),
                    onSurface = Color(0xFFE8E8EE),
                ) else lightColorScheme(
                    primary = accentColor,
                    onPrimary = Color.White,
                    background = Color(0xFFF5F6F8),
                    surface = Color(0xFFFFFFFF),
                    onBackground = Color(0xFF111111),
                    onSurface = Color(0xFF111111),
                )
            }
        }
    }

    // Animated theme transition
    val animatedColorScheme = colorScheme.copy(
        primary = animateColorAsState(colorScheme.primary, tween(500), label = "primary").value,
        onPrimary = animateColorAsState(colorScheme.onPrimary, tween(500), label = "onPrimary").value,
        secondary = animateColorAsState(colorScheme.secondary, tween(500), label = "secondary").value,
        tertiary = animateColorAsState(colorScheme.tertiary, tween(500), label = "tertiary").value,
        background = animateColorAsState(colorScheme.background, tween(500), label = "background").value,
        surface = animateColorAsState(colorScheme.surface, tween(500), label = "surface").value,
        surfaceVariant = animateColorAsState(colorScheme.surfaceVariant, tween(500), label = "surfaceVariant").value,
        onBackground = animateColorAsState(colorScheme.onBackground, tween(500), label = "onBackground").value,
        onSurface = animateColorAsState(colorScheme.onSurface, tween(500), label = "onSurface").value,
        onSurfaceVariant = animateColorAsState(colorScheme.onSurfaceVariant, tween(500), label = "onSurfaceVariant").value,
        outline = animateColorAsState(colorScheme.outline, tween(500), label = "outline").value,
        error = animateColorAsState(colorScheme.error, tween(500), label = "error").value,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            try {
                val context = view.context
                val activity = context as? Activity ?: return@SideEffect
                val window = activity.window
                // Use WindowCompat for edge-to-edge — statusBarColor/navigationBarColor
                // are deprecated since API 35. The system handles bar colors automatically
                // when using transparent bars with WindowCompat.
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    window.statusBarColor = animatedColorScheme.background.toArgb()
                    window.navigationBarColor = animatedColorScheme.background.toArgb()
                }
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            } catch (e: Exception) {
                android.util.Log.e("NexaTheme", "Theme error: ${e.message}")
            }
        }
    }

    CompositionLocalProvider(LocalAccentColor provides accentColor) {
        MaterialTheme(
            colorScheme = animatedColorScheme,
            content = content
        )
    }
}
