package com.nexa.ai.ui.theme

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.nexa.ai.viewmodel.ThemeMode

// ── Futuristic Accent: Emerald-Teal (Custom themes) ──
val NexaAccent = Color(0xFF00F5A0)       // Primary neon green
val NexaAccentLight = Color(0xFF66FFD0)  // Lighter variant
val NexaAccentDark = Color(0xFF00C896)   // Darker variant
val NexaGlow = Color(0xFF00F5A0)         // Glow color

// ── Custom Dark Theme ──
private val DarkColorScheme = darkColorScheme(
    primary = NexaAccent,
    onPrimary = Color.Black,
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

// ── Custom Light Theme ──
private val LightColorScheme = lightColorScheme(
    primary = NexaAccentDark,
    onPrimary = Color.White,
    background = Color(0xFFF2F4F7), // Light grayish/silver instead of pure white
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EAED),
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainer = Color(0xFFF1F3F5),
    surfaceContainerHigh = Color(0xFFE9ECEF),
    onBackground = Color(0xFF1A1C1E),
    onSurface = Color(0xFF1A1C1E),
    onSurfaceVariant = Color(0xFF44474E), // Darker gray for better contrast
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    error = Color(0xFFBA1A1A),
    inverseSurface = Color(0xFF2F3033),
)

/** User message bubble color — must be opaque enough for white text readability. */
val NexaUserBubbleLight = Color(0xFF00C896)   // Solid accent for light theme user bubbles
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
//  RESPONSIVE BREAKPOINTS
// ═══════════════════════════════════════

enum class ScreenSize { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberScreenSize(): ScreenSize {
    val config = LocalConfiguration.current
    return when {
        config.screenWidthDp < 600 -> ScreenSize.COMPACT
        config.screenWidthDp < 840 -> ScreenSize.MEDIUM
        else -> ScreenSize.EXPANDED
    }
}

@Composable
fun <T> responsive(compact: T, medium: T, expanded: T): T {
    return when (rememberScreenSize()) {
        ScreenSize.COMPACT -> compact
        ScreenSize.MEDIUM -> medium
        ScreenSize.EXPANDED -> expanded
    }
}

object NexaPadding {
    @Composable fun horizontal(): Dp = responsive(16.dp, 24.dp, 32.dp)
    @Composable fun vertical(): Dp = responsive(12.dp, 16.dp, 20.dp)
    @Composable fun content(): Dp = responsive(12.dp, 16.dp, 24.dp)
    @Composable fun card(): Dp = responsive(12.dp, 14.dp, 16.dp)
}

object NexaTextScale {
    @Composable fun body(): Float = responsive(1f, 1.05f, 1.1f)
    @Composable fun title(): Float = responsive(1f, 1.1f, 1.2f)
}

// ═══════════════════════════════════════
//  THEME COMPOSABLE
// ═══════════════════════════════════════

@Composable
fun NexaTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark
    }

    val colorScheme = when (themeMode) {
        // ── Custom neon themes: always use our fixed palettes ──
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.LIGHT -> LightColorScheme
        // ── System: use Material You dynamic colors if available, else fallback ──
        ThemeMode.SYSTEM -> {
            if (supportsDynamicColors()) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
    }

    // Animated theme transition
    val animatedColorScheme = colorScheme.copy(
        primary = animateColorAsState(colorScheme.primary, tween(500), label = "primary").value,
        onPrimary = animateColorAsState(colorScheme.onPrimary, tween(500), label = "onPrimary").value,
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
                window.statusBarColor = animatedColorScheme.background.toArgb()
                window.navigationBarColor = animatedColorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            } catch (e: Exception) {
                android.util.Log.e("NexaTheme", "Theme error: ${e.message}")
            }
        }
    }

    MaterialTheme(
        colorScheme = animatedColorScheme,
        content = content
    )
}
