package com.nexa.ai.ui.theme

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.runtime.compositionLocalOf
import com.nexa.ai.viewmodel.ThemeMode

// ── Futuristic Accent: Emerald-Teal (Custom themes) ──
val NexaAccent = Color(0xFF00F5A0)       // Primary neon green
val NexaAccentLight = Color(0xFF66FFD0)  // Lighter variant
val NexaAccentDark = Color(0xFF00C896)   // Darker variant
val NexaGlow = Color(0xFF00F5A0)         // Glow color

/** CompositionLocal providing the effective accent color for the entire app. */
val LocalAccentColor = compositionLocalOf { NexaAccent }

// ── Custom Dark Theme ──
private val DarkColorScheme = darkColorScheme(
    primary = NexaAccent,
    onPrimary = Color.Black,
    background = Color(0xFF050508),      // Deep futuristic black
    surface = Color(0xFF0F0F14),         // Dark card surface
    surfaceVariant = Color(0xFF16161F),  // Slightly lighter variant
    surfaceContainerLow = Color(0xFF08080C),
    surfaceContainer = Color(0xFF0F0F14),
    surfaceContainerHigh = Color(0xFF16161F),
    onBackground = Color(0xFFEEEEEE),    // Light text
    onSurface = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF9090A0),
    outline = Color(0xFF30303A),
    outlineVariant = Color(0xFF25252F),
    error = Color(0xFFFF4466),
    inverseSurface = Color(0xFFEEEEEE),
)

// ── Custom Light Theme ──
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00875A),        // Darker green for better contrast on white
    onPrimary = Color.White,
    background = Color(0xFFF5F6F8),     // Subtle off-white
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EAED),
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainer = Color(0xFFF1F3F5),
    surfaceContainerHigh = Color(0xFFE9ECEF),
    onBackground = Color(0xFF111111),   // Near-black for max contrast
    onSurface = Color(0xFF111111),      // Near-black for max contrast
    onSurfaceVariant = Color(0xFF333333), // Dark gray, not medium gray
    outline = Color(0xFF666666),        // Visible borders
    outlineVariant = Color(0xFFBBBBBB),
    error = Color(0xFFBA1A1A),
    inverseSurface = Color(0xFF2F3033),
)

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
    accentColor: Color = NexaAccent,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark
    }

    // Determine the effective primary accent to use
    val effectivePrimary = if (darkTheme) {
        accentColor
    } else {
        // For light theme, darken the accent for better contrast
        accentColor.copy(red = (accentColor.red * 0.55f), green = (accentColor.green * 0.55f), blue = (accentColor.blue * 0.55f))
    }
    // Determine onPrimary: white on dark backgrounds, black on light accent colors
    val effectiveOnPrimary = if (darkTheme) Color.Black else Color.White

    val baseScheme = when (themeMode) {
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

    // Override primary colors with the user-selected accent color
    val colorScheme = baseScheme.copy(
        primary = effectivePrimary,
        onPrimary = effectiveOnPrimary,
        secondary = effectivePrimary.copy(alpha = 0.8f),
        tertiary = effectivePrimary,
        inversePrimary = effectivePrimary.copy(alpha = 0.7f)
    )

    // Animated theme transition (Simplified for stability)
    val animatedColorScheme = colorScheme

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

    MaterialTheme(
        colorScheme = animatedColorScheme,
    ) {
        CompositionLocalProvider(LocalAccentColor provides accentColor) {
            content()
        }
    }
}
