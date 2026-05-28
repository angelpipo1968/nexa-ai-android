package com.nexa.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * NEXA AI — Unified Adaptive Screen System
 */

// ═══════════════════════════════════════
//  SCREEN SIZE CLASSIFICATION
// ═══════════════════════════════════════

enum class ScreenSizeClass { COMPACT, MEDIUM, EXPANDED }

enum class OrientationMode { PORTRAIT, LANDSCAPE }

data class AdaptiveInfo(
    val screenSizeClass: ScreenSizeClass,
    val orientation: OrientationMode,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val isTablet: Boolean,
    val isFoldable: Boolean,
    val isPhone: Boolean,
    val shouldUseDualPane: Boolean,
    val shouldShowPermanentDrawer: Boolean = isTablet && orientation == OrientationMode.LANDSCAPE
)

@Composable
fun rememberAdaptiveInfo(): AdaptiveInfo {
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp
    val heightDp = config.screenHeightDp
    val screenSizeClass = when {
        widthDp < 600 -> ScreenSizeClass.COMPACT
        widthDp < 840 -> ScreenSizeClass.MEDIUM
        else -> ScreenSizeClass.EXPANDED
    }
    val orientation = if (widthDp >= heightDp) OrientationMode.LANDSCAPE else OrientationMode.PORTRAIT
    val isTablet = widthDp >= 840
    val isFoldable = widthDp in 600..839
    val isPhone = widthDp < 600
    val shouldUseDualPane = screenSizeClass == ScreenSizeClass.EXPANDED ||
        (screenSizeClass == ScreenSizeClass.MEDIUM && orientation == OrientationMode.LANDSCAPE)

    return AdaptiveInfo(
        screenSizeClass = screenSizeClass,
        orientation = orientation,
        screenWidthDp = widthDp,
        screenHeightDp = heightDp,
        isTablet = isTablet,
        isFoldable = isFoldable,
        isPhone = isPhone,
        shouldUseDualPane = shouldUseDualPane
    )
}

val LocalWindowAdaptiveInfo = compositionLocalOf<AdaptiveInfo> {
    error("No AdaptiveInfo provided")
}

@Composable
fun ProvideWindowAdaptiveInfo(content: @Composable () -> Unit) {
    val adaptiveInfo = rememberAdaptiveInfo()
    CompositionLocalProvider(LocalWindowAdaptiveInfo provides adaptiveInfo) {
        content()
    }
}

// ═══════════════════════════════════════
//  ADAPTIVE DIMENSIONS
// ═══════════════════════════════════════

@Composable
fun adaptive(compact: Dp, medium: Dp = compact * 1.2f, expanded: Dp = compact * 1.5f): Dp {
    return when (LocalWindowAdaptiveInfo.current.screenSizeClass) {
        ScreenSizeClass.COMPACT -> compact
        ScreenSizeClass.MEDIUM -> medium
        ScreenSizeClass.EXPANDED -> expanded
    }
}

@Composable
fun adaptiveDimension(compact: Dp, medium: Dp = compact * 1.2f, expanded: Dp = compact * 1.5f): Dp =
    adaptive(compact, medium, expanded)

@Composable
fun adaptiveText(compact: TextUnit, medium: TextUnit = (compact.value * 1.1f).sp, expanded: TextUnit = (compact.value * 1.2f).sp): TextUnit {
    return when (LocalWindowAdaptiveInfo.current.screenSizeClass) {
        ScreenSizeClass.COMPACT -> compact
        ScreenSizeClass.MEDIUM -> medium
        ScreenSizeClass.EXPANDED -> expanded
    }
}

object AdaptiveDimens {
    @Composable fun horizontalPadding(): Dp = adaptive(16.dp, 24.dp, 40.dp)
    @Composable fun verticalPadding(): Dp = adaptive(12.dp, 16.dp, 24.dp)
    @Composable fun spacingSm(): Dp = adaptive(4.dp, 6.dp, 8.dp)
    @Composable fun spacingMd(): Dp = adaptive(8.dp, 10.dp, 12.dp)
    @Composable fun spacingLg(): Dp = adaptive(16.dp, 20.dp, 24.dp)
    @Composable fun spacingXl(): Dp = adaptive(24.dp, 32.dp, 40.dp)
    @Composable fun spacingXxl(): Dp = adaptive(32.dp, 48.dp, 64.dp)
    @Composable fun sectionSpacing(): Dp = adaptive(20.dp, 28.dp, 36.dp)
    @Composable fun maxContentWidth(): Dp = 800.dp
    @Composable fun maxAuthContentWidth(): Dp = 450.dp
    @Composable fun permanentDrawerWidth(): Dp = 320.dp
    @Composable fun drawerWidth(): Dp = adaptive(300.dp, 340.dp, 380.dp)
    @Composable fun avatarSmall(): Dp = adaptive(32.dp, 36.dp, 40.dp)
    @Composable fun iconSmall(): Dp = 16.dp
    @Composable fun iconMedium(): Dp = 24.dp
    @Composable fun iconLarge(): Dp = adaptive(32.dp, 40.dp, 48.dp)
    @Composable fun logoSize(): Dp = adaptive(60.dp, 80.dp, 100.dp)
    @Composable fun buttonHeight(): Dp = adaptive(48.dp, 52.dp, 56.dp)
    @Composable fun cornerSmall(): Dp = 8.dp
    @Composable fun cornerMedium(): Dp = 12.dp
    @Composable fun cornerLarge(): Dp = 24.dp
}

object AdaptiveTypography {
    @Composable fun labelSmall(): TextUnit = adaptiveText(11.sp, 12.sp, 13.sp)
    @Composable fun labelMedium(): TextUnit = adaptiveText(12.sp, 13.sp, 14.sp)
    @Composable fun labelLarge(): TextUnit = adaptiveText(14.sp, 15.sp, 16.sp)
    @Composable fun bodySmall(): TextUnit = adaptiveText(12.sp, 13.sp, 14.sp)
    @Composable fun bodyMedium(): TextUnit = adaptiveText(14.sp, 15.sp, 16.sp)
    @Composable fun caption(): TextUnit = adaptiveText(10.sp, 11.sp, 12.sp)
    @Composable fun headlineSmall(): TextUnit = adaptiveText(18.sp, 20.sp, 24.sp)
    @Composable fun headlineMedium(): TextUnit = adaptiveText(20.sp, 24.sp, 28.sp)
    @Composable fun displayLarge(): TextUnit = adaptiveText(24.sp, 28.sp, 32.sp)
}

@Composable
fun CenteredContent(
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (BoxScope.() -> Unit)
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxSize(),
            content = content
        )
    }
}

object NexaSpacing {
    @Composable fun screenHorizontal(): Dp = AdaptiveDimens.horizontalPadding()
    @Composable fun screenVertical(): Dp = AdaptiveDimens.verticalPadding()
    @Composable fun contentPadding(): Dp = AdaptiveDimens.spacingLg()
    @Composable fun cardPadding(): Dp = AdaptiveDimens.spacingMd()
    @Composable fun buttonHeight(): Dp = AdaptiveDimens.buttonHeight()
    @Composable fun itemSpacing(): Dp = AdaptiveDimens.spacingMd()
    @Composable fun sectionSpacing(): Dp = AdaptiveDimens.sectionSpacing()
}

object NexaSizes {
    @Composable fun messageBubbleMaxWidth(): Float = when (LocalWindowAdaptiveInfo.current.screenSizeClass) {
        ScreenSizeClass.COMPACT -> 0.85f
        ScreenSizeClass.MEDIUM -> 0.75f
        ScreenSizeClass.EXPANDED -> 0.65f
    }
    @Composable fun emptyStateTopPadding(): Dp = adaptive(60.dp, 80.dp, 120.dp)
}

@Composable
fun chatContentPadding(): PaddingValues = PaddingValues(
    horizontal = NexaSpacing.screenHorizontal(),
    vertical = NexaSpacing.screenVertical()
)

/** @deprecated */
object AdaptivePadding {
    @Composable fun horizontal(): Dp = AdaptiveDimens.horizontalPadding()
    @Composable fun vertical(): Dp = AdaptiveDimens.verticalPadding()
}
