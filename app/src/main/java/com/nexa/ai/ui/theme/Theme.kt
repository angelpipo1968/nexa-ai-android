package com.nexa.ai.ui.theme

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.nexa.ai.viewmodel.ThemeMode

// ── Futuristic Accent: Emerald-Teal ──
val NexaAccent = Color(0xFF00D4AA)       // Primary teal-cyan
val NexaAccentLight = Color(0xFF33FFD0)  // Lighter variant
val NexaAccentDark = Color(0xFF00A882)   // Darker variant
val NexaGlow = Color(0xFF00D4AA)         // Glow color

private val DarkColorScheme = darkColorScheme(
    primary = NexaAccent,
    onPrimary = Color.Black,
    background = Color(0xFF050508),
    surface = Color(0xFF0D0D12),
    surfaceVariant = Color(0xFF161620),
    surfaceContainerLow = Color(0xFF0A0A0F),
    surfaceContainer = Color(0xFF121218),
    surfaceContainerHigh = Color(0xFF1A1A24),
    onBackground = Color(0xFFE8E8EC),
    onSurface = Color(0xFFE8E8EC),
    onSurfaceVariant = Color(0xFF6B6B80),
    outline = Color(0xFF1E1E2A),
    outlineVariant = Color(0xFF2A2A38),
    error = Color(0xFFFF4D6A),
    inverseSurface = Color(0xFFE8E8EC),
)

private val LightColorScheme = lightColorScheme(
    primary = NexaAccentDark,
    onPrimary = Color.White,
    background = Color(0xFFF8F9FC),
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

// ═══════════════════════════════════════
//  RESPONSIVE BREAKPOINTS
// ═══════════════════════════════════════

enum class ScreenSize { COMPACT, MEDIUM, EXPANDED }

/** Returns the current screen size category based on width. */
@Composable
fun rememberScreenSize(): ScreenSize {
    val config = LocalConfiguration.current
    return when {
        config.screenWidthDp < 600 -> ScreenSize.COMPACT   // Phone portrait
        config.screenWidthDp < 840 -> ScreenSize.MEDIUM    // Tablet portrait / large phone landscape
        else -> ScreenSize.EXPANDED                         // Tablet landscape / desktop
    }
}

/** Responsive values based on screen size. */
@Composable
fun <T> responsive(compact: T, medium: T, expanded: T): T {
    return when (rememberScreenSize()) {
        ScreenSize.COMPACT -> compact
        ScreenSize.MEDIUM -> medium
        ScreenSize.EXPANDED -> expanded
    }
}

/** Responsive padding values. */
object NexaPadding {
    @Composable fun horizontal(): Dp = responsive(16.dp, 24.dp, 32.dp)
    @Composable fun vertical(): Dp = responsive(12.dp, 16.dp, 20.dp)
    @Composable fun content(): Dp = responsive(12.dp, 16.dp, 24.dp)
    @Composable fun card(): Dp = responsive(12.dp, 14.dp, 16.dp)
}

/** Responsive text sizes (sp multipliers). */
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

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            try {
                val context = view.context
                val activity = context as? Activity ?: return@SideEffect
                val window = activity.window
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            } catch (e: Exception) {
                android.util.Log.e("NexaTheme", "Theme error: ${e.message}")
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
