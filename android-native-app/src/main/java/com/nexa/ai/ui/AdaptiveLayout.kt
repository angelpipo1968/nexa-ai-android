package com.nexa.ai.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * NEXA AI — Unified Adaptive Screen System
 * Automatically adjusts layout, spacing, typography and component sizes
 * for ANY screen: phones, foldables, tablets, landscape, portrait.
 *
 * Breakpoints follow Material 3 guidelines:
 * - COMPACT: < 600dp width (phone portrait)
 * - MEDIUM: 600-839dp width (phone landscape, foldable, small tablet portrait)
 * - EXPANDED: >= 840dp width (tablet landscape, large foldable)
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
    val shouldUseDualPane: Boolean  // true for MEDIUM landscape and EXPANDED
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

// ═══════════════════════════════════════
//  BACKWARD COMPATIBILITY — DeviceType
// ═══════════════════════════════════════

enum class DeviceType { PHONE, TABLET, FOLDABLE }

/** @deprecated Use rememberAdaptiveInfo() instead for richer screen info */
@Composable
fun rememberDeviceType(): DeviceType {
    val info = rememberAdaptiveInfo()
    return when {
        info.isTablet -> DeviceType.TABLET
        info.isFoldable -> DeviceType.FOLDABLE
        else -> DeviceType.PHONE
    }
}

// ═══════════════════════════════════════
//  ADAPTIVE DIMENSIONS
// ═══════════════════════════════════════

/** Adaptive dimension based on screen size class */
@Composable
fun adaptive(
    compact: Dp,
    medium: Dp = compact * 1.3f,
    expanded: Dp = compact * 1.6f
): Dp {
    return when (rememberAdaptiveInfo().screenSizeClass) {
        ScreenSizeClass.COMPACT -> compact
        ScreenSizeClass.MEDIUM -> medium
        ScreenSizeClass.EXPANDED -> expanded
    }
}

/** Adaptive font size based on screen size class */
@Composable
fun adaptiveText(
    compact: TextUnit,
    medium: TextUnit = (compact.value * 1.08f).sp,
    expanded: TextUnit = (compact.value * 1.15f).sp
): TextUnit {
    return when (rememberAdaptiveInfo().screenSizeClass) {
        ScreenSizeClass.COMPACT -> compact
        ScreenSizeClass.MEDIUM -> medium
        ScreenSizeClass.EXPANDED -> expanded
    }
}

// ═══════════════════════════════════════
//  ADAPTIVE SPACING SYSTEM
// ═══════════════════════════════════════

object NexaSpacing {
    /** Screen horizontal margin */
    @Composable fun screenHorizontal(): Dp = adaptive(16.dp, 24.dp, 32.dp)
    /** Screen vertical margin */
    @Composable fun screenVertical(): Dp = adaptive(8.dp, 12.dp, 16.dp)
    /** Content padding inside cards/surfaces */
    @Composable fun contentPadding(): Dp = adaptive(12.dp, 16.dp, 20.dp)
    /** Card internal padding */
    @Composable fun cardPadding(): Dp = adaptive(12.dp, 14.dp, 16.dp)
    /** Button height */
    @Composable fun buttonHeight(): Dp = adaptive(36.dp, 40.dp, 44.dp)
    /** Space between items */
    @Composable fun itemSpacing(): Dp = adaptive(8.dp, 10.dp, 12.dp)
    /** Section spacing */
    @Composable fun sectionSpacing(): Dp = adaptive(16.dp, 20.dp, 24.dp)
}

/** Max content width — centers content on wide screens */
@Composable
fun maxContentWidth(): Dp = when (rememberAdaptiveInfo().screenSizeClass) {
    ScreenSizeClass.COMPACT -> Dp.Unspecified
    ScreenSizeClass.MEDIUM -> 600.dp
    ScreenSizeClass.EXPANDED -> 840.dp
}

/** Drawer width — adapts to screen */
@Composable
fun adaptiveDrawerWidth(): Dp = when (rememberAdaptiveInfo().screenSizeClass) {
    ScreenSizeClass.COMPACT -> 300.dp
    ScreenSizeClass.MEDIUM -> 340.dp
    ScreenSizeClass.EXPANDED -> 380.dp
}

/** Chat messages content padding */
@Composable
fun chatContentPadding(): PaddingValues {
    val h = NexaSpacing.screenHorizontal()
    val v = NexaSpacing.screenVertical()
    return PaddingValues(horizontal = h, vertical = v)
}

// ═══════════════════════════════════════
//  ADAPTIVE TYPOGRAPHY SCALE
// ═══════════════════════════════════════

/** Adaptive Float value based on screen size class */
@Composable
fun adaptiveFloat(
    compact: Float,
    medium: Float = compact * 1.08f,
    expanded: Float = compact * 1.15f
): Float {
    return when (rememberAdaptiveInfo().screenSizeClass) {
        ScreenSizeClass.COMPACT -> compact
        ScreenSizeClass.MEDIUM -> medium
        ScreenSizeClass.EXPANDED -> expanded
    }
}

object NexaTypographyScale {
    @Composable fun bodyScale(): Float = adaptiveFloat(1f, 1.05f, 1.1f)
    @Composable fun titleScale(): Float = adaptiveFloat(1f, 1.1f, 1.2f)
    @Composable fun headlineScale(): Float = adaptiveFloat(1f, 1.12f, 1.25f)
}

// ═══════════════════════════════════════
//  ADAPTIVE COMPONENT SIZES
// ═══════════════════════════════════════

object NexaSizes {
    /** Message bubble max width as fraction of screen */
    @Composable fun messageBubbleMaxWidth(): Float = when (rememberAdaptiveInfo().screenSizeClass) {
        ScreenSizeClass.COMPACT -> 0.85f
        ScreenSizeClass.MEDIUM -> 0.72f
        ScreenSizeClass.EXPANDED -> 0.62f
    }
    /** Icon button size */
    @Composable fun iconButtonSize(): Dp = adaptive(32.dp, 36.dp, 40.dp)
    /** Avatar size */
    @Composable fun avatarSize(): Dp = adaptive(36.dp, 40.dp, 44.dp)
    /** Top bar height */
    @Composable fun topBarHeight(): Dp = adaptive(56.dp, 60.dp, 64.dp)
    /** Input bar min height */
    @Composable fun inputBarMinHeight(): Dp = adaptive(52.dp, 56.dp, 60.dp)
    /** Quick action chip height */
    @Composable fun chipHeight(): Dp = adaptive(36.dp, 40.dp, 44.dp)
    /** Empty state top padding */
    @Composable fun emptyStateTopPadding(): Dp = adaptive(80.dp, 60.dp, 40.dp)
}

// ═══════════════════════════════════════
//  BACKWARD COMPATIBILITY (deprecated)
// ═══════════════════════════════════════

/** @deprecated Use NexaSpacing instead */
object AdaptivePadding {
    @Composable fun horizontal(): Dp = NexaSpacing.screenHorizontal()
    @Composable fun vertical(): Dp = NexaSpacing.screenVertical()
    @Composable fun card(): Dp = NexaSpacing.cardPadding()
    @Composable fun button(): Dp = NexaSpacing.buttonHeight()
}
