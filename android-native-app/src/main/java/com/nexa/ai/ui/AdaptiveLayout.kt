package com.nexa.ai.ui

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*

/**
 * ═══════════════════════════════════════════════════════════════
 *  NEXA AI — Comprehensive Adaptive Layout System
 *  Supports: Phones, Foldables, Tablets, Landscape
 *  Based on Material 3 WindowSizeClass
 * ═══════════════════════════════════════════════════════════════
 */

// ── Breakpoint Constants ──────────────────────────────────────
object NexaBreakpoints {
    val COMPACT_MAX = 600.dp
    val MEDIUM_MAX = 840.dp
    // > 840dp = EXPANDED
}

// ── Window Adaptive Info ──────────────────────────────────────

/**
 * Holds all adaptive info derived from WindowSizeClass.
 * Passed down the composable tree via CompositionLocal.
 */
data class WindowAdaptiveInfo(
    val widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    val heightSizeClass: WindowHeightSizeClass = WindowHeightSizeClass.Compact,
    val screenWidthDp: Int = 0,
    val screenHeightDp: Int = 0
) {
    /** Whether the width is Compact (< 600dp) — typical phone portrait */
    val isCompact: Boolean get() = widthSizeClass == WindowWidthSizeClass.Compact

    /** Whether the width is Medium (600-840dp) — foldable, phone landscape, small tablet */
    val isMedium: Boolean get() = widthSizeClass == WindowWidthSizeClass.Medium

    /** Whether the width is Expanded (> 840dp) — tablet, desktop */
    val isExpanded: Boolean get() = widthSizeClass == WindowWidthSizeClass.Expanded

    /** Is the device in landscape orientation? */
    val isLandscape: Boolean get() = screenWidthDp > screenHeightDp

    /** Should we use two-pane layout? */
    val shouldUseTwoPane: Boolean get() = isExpanded || (isMedium && isLandscape)

    /** Should we show permanent drawer (not modal)? */
    val shouldShowPermanentDrawer: Boolean get() = isExpanded

    /** Should we use navigation rail instead of bottom nav? */
    val shouldUseNavigationRail: Boolean get() = isExpanded

    /** Number of columns for grid layouts */
    val gridColumns: Int get() = when {
        isExpanded -> 3
        isMedium -> 2
        else -> 1
    }

    /** Quick-action columns */
    val quickActionColumns: Int get() = when {
        isExpanded -> 4
        isMedium -> 3
        else -> 2
    }
}

/** CompositionLocal for accessing adaptive info anywhere in the tree. */
val LocalWindowAdaptiveInfo = compositionLocalOf { WindowAdaptiveInfo() }

// ── Legacy DeviceType (kept for backward compatibility) ──────

enum class DeviceType { PHONE, TABLET, FOLDABLE }

@Composable
fun rememberDeviceType(): DeviceType {
    val adaptiveInfo = LocalWindowAdaptiveInfo.current
    return when {
        adaptiveInfo.isExpanded -> DeviceType.TABLET
        adaptiveInfo.isMedium -> DeviceType.FOLDABLE
        else -> DeviceType.PHONE
    }
}

// ── Adaptive Dimensions ───────────────────────────────────────

/**
 * Comprehensive adaptive dimension system.
 * All UI measurements should go through this object.
 */
object AdaptiveDimens {

    // ── Padding ──
    @Composable fun horizontalPadding(): Dp = adaptiveDimension(
        compact = 16.dp, medium = 24.dp, expanded = 32.dp
    )
    @Composable fun verticalPadding(): Dp = adaptiveDimension(
        compact = 8.dp, medium = 12.dp, expanded = 16.dp
    )
    @Composable fun cardPadding(): Dp = adaptiveDimension(
        compact = 12.dp, medium = 14.dp, expanded = 16.dp
    )
    @Composable fun cardInnerPadding(): Dp = adaptiveDimension(
        compact = 14.dp, medium = 16.dp, expanded = 18.dp
    )
    @Composable fun sectionSpacing(): Dp = adaptiveDimension(
        compact = 28.dp, medium = 32.dp, expanded = 36.dp
    )

    // ── Buttons & Touch Targets ──
    @Composable fun buttonHeight(): Dp = adaptiveDimension(
        compact = 44.dp, medium = 48.dp, expanded = 52.dp
    )
    @Composable fun iconButtonSize(): Dp = adaptiveDimension(
        compact = 36.dp, medium = 40.dp, expanded = 44.dp
    )
    @Composable fun touchTargetSize(): Dp = adaptiveDimension(
        compact = 44.dp, medium = 48.dp, expanded = 52.dp
    )
    @Composable fun fabSize(): Dp = adaptiveDimension(
        compact = 56.dp, medium = 60.dp, expanded = 64.dp
    )

    // ── Icons ──
    @Composable fun iconSmall(): Dp = adaptiveDimension(
        compact = 14.dp, medium = 16.dp, expanded = 18.dp
    )
    @Composable fun iconMedium(): Dp = adaptiveDimension(
        compact = 18.dp, medium = 20.dp, expanded = 22.dp
    )
    @Composable fun iconLarge(): Dp = adaptiveDimension(
        compact = 24.dp, medium = 28.dp, expanded = 32.dp
    )

    // ── Chat-Specific ──
    @Composable fun messageBubbleMaxWidth(): Dp = adaptiveDimension(
        compact = 280.dp, medium = 420.dp, expanded = 600.dp
    )
    @Composable fun chatListPadding(): Dp = adaptiveDimension(
        compact = 16.dp, medium = 20.dp, expanded = 24.dp
    )
    @Composable fun messageSpacing(): Dp = adaptiveDimension(
        compact = 10.dp, medium = 12.dp, expanded = 14.dp
    )
    @Composable fun inputBarHeight(): Dp = adaptiveDimension(
        compact = 52.dp, medium = 56.dp, expanded = 60.dp
    )

    // ── Drawer ──
    @Composable fun drawerWidth(): Dp = adaptiveDimension(
        compact = 300.dp, medium = 320.dp, expanded = 340.dp
    )
    @Composable fun permanentDrawerWidth(): Dp = adaptiveDimension(
        compact = 300.dp, medium = 300.dp, expanded = 320.dp
    )

    // ── Navigation ──
    @Composable fun navRailWidth(): Dp = adaptiveDimension(
        compact = 0.dp, medium = 0.dp, expanded = 80.dp
    )
    @Composable fun bottomNavHeight(): Dp = adaptiveDimension(
        compact = 64.dp, medium = 68.dp, expanded = 0.dp
    )

    // ── Max Content Width (centering on wide screens) ──
    @Composable fun maxContentWidth(): Dp = adaptiveDimension(
        compact = Dp.Unspecified, medium = 600.dp, expanded = 720.dp
    )
    @Composable fun maxAuthContentWidth(): Dp = adaptiveDimension(
        compact = Dp.Unspecified, medium = 480.dp, expanded = 520.dp
    )
    @Composable fun maxSettingsWidth(): Dp = adaptiveDimension(
        compact = Dp.Unspecified, medium = 600.dp, expanded = 840.dp
    )

    // ── Corner Radius ──
    @Composable fun cornerSmall(): Dp = adaptiveDimension(
        compact = 8.dp, medium = 10.dp, expanded = 12.dp
    )
    @Composable fun cornerMedium(): Dp = adaptiveDimension(
        compact = 12.dp, medium = 14.dp, expanded = 16.dp
    )
    @Composable fun cornerLarge(): Dp = adaptiveDimension(
        compact = 16.dp, medium = 18.dp, expanded = 20.dp
    )
    @Composable fun cornerExtraLarge(): Dp = adaptiveDimension(
        compact = 20.dp, medium = 22.dp, expanded = 24.dp
    )

    // ── Avatar / Logo ──
    @Composable fun avatarSmall(): Dp = adaptiveDimension(
        compact = 32.dp, medium = 36.dp, expanded = 40.dp
    )
    @Composable fun avatarMedium(): Dp = adaptiveDimension(
        compact = 40.dp, medium = 48.dp, expanded = 56.dp
    )
    @Composable fun avatarLarge(): Dp = adaptiveDimension(
        compact = 56.dp, medium = 64.dp, expanded = 72.dp
    )
    @Composable fun logoSize(): Dp = adaptiveDimension(
        compact = 72.dp, medium = 80.dp, expanded = 88.dp
    )

    // ── Spacing ──
    @Composable fun spacingXs(): Dp = adaptiveDimension(compact = 4.dp, medium = 6.dp, expanded = 8.dp)
    @Composable fun spacingSm(): Dp = adaptiveDimension(compact = 8.dp, medium = 10.dp, expanded = 12.dp)
    @Composable fun spacingMd(): Dp = adaptiveDimension(compact = 12.dp, medium = 14.dp, expanded = 16.dp)
    @Composable fun spacingLg(): Dp = adaptiveDimension(compact = 16.dp, medium = 20.dp, expanded = 24.dp)
    @Composable fun spacingXl(): Dp = adaptiveDimension(compact = 24.dp, medium = 28.dp, expanded = 32.dp)
    @Composable fun spacingXxl(): Dp = adaptiveDimension(compact = 32.dp, medium = 40.dp, expanded = 48.dp)
}

// ── Adaptive Typography ───────────────────────────────────────

object AdaptiveTypography {
    @Composable fun displayLarge(): TextUnit = adaptiveText(compact = 28.sp, medium = 30.sp, expanded = 32.sp)
    @Composable fun displayMedium(): TextUnit = adaptiveText(compact = 24.sp, medium = 26.sp, expanded = 28.sp)
    @Composable fun headlineLarge(): TextUnit = adaptiveText(compact = 20.sp, medium = 22.sp, expanded = 24.sp)
    @Composable fun headlineMedium(): TextUnit = adaptiveText(compact = 18.sp, medium = 19.sp, expanded = 20.sp)
    @Composable fun headlineSmall(): TextUnit = adaptiveText(compact = 16.sp, medium = 17.sp, expanded = 18.sp)
    @Composable fun bodyLarge(): TextUnit = adaptiveText(compact = 16.sp, medium = 17.sp, expanded = 18.sp)
    @Composable fun bodyMedium(): TextUnit = adaptiveText(compact = 14.sp, medium = 15.sp, expanded = 16.sp)
    @Composable fun bodySmall(): TextUnit = adaptiveText(compact = 12.sp, medium = 13.sp, expanded = 14.sp)
    @Composable fun labelLarge(): TextUnit = adaptiveText(compact = 14.sp, medium = 14.sp, expanded = 15.sp)
    @Composable fun labelMedium(): TextUnit = adaptiveText(compact = 12.sp, medium = 12.sp, expanded = 13.sp)
    @Composable fun labelSmall(): TextUnit = adaptiveText(compact = 10.sp, medium = 10.sp, expanded = 11.sp)
    @Composable fun caption(): TextUnit = adaptiveText(compact = 9.sp, medium = 9.sp, expanded = 10.sp)

    // Chat-specific
    @Composable fun chatMessage(): TextUnit = adaptiveText(compact = 15.sp, medium = 15.sp, expanded = 16.sp)
    @Composable fun chatMessageLineHeight(): TextUnit = adaptiveText(compact = 22.sp, medium = 22.sp, expanded = 24.sp)
    @Composable fun chatInput(): TextUnit = adaptiveText(compact = 14.sp, medium = 14.sp, expanded = 15.sp)
}

// ── Core Adaptive Functions ───────────────────────────────────

/**
 * Returns a Dp value based on the current WindowSizeClass breakpoint.
 */
@Composable
fun adaptiveDimension(
    compact: Dp,
    medium: Dp = compact * 1.15f,
    expanded: Dp = compact * 1.3f
): Dp {
    val info = LocalWindowAdaptiveInfo.current
    return when {
        info.isExpanded -> expanded
        info.isMedium -> medium
        else -> compact
    }
}

/** Adaptive dimension that scales based on device type (legacy support). */
@Composable
fun adaptive(
    phone: Dp,
    tablet: Dp = phone * 1.3f,
    foldable: Dp = phone * 1.15f
): Dp {
    return when (rememberDeviceType()) {
        DeviceType.PHONE -> phone
        DeviceType.FOLDABLE -> foldable
        DeviceType.TABLET -> tablet
    }
}

/** Adaptive font size that scales based on screen density. */
@Composable
fun adaptiveText(
    phone: TextUnit,
    tablet: TextUnit = (phone.value * 1.15f).sp,
    foldable: TextUnit = (phone.value * 1.05f).sp
): TextUnit {
    val info = LocalWindowAdaptiveInfo.current
    return when {
        info.isExpanded -> tablet
        info.isMedium -> foldable
        else -> phone
    }
}

/** Max content width for tablets — centers content on wide screens. */
@Composable
fun maxContentWidth(): Dp {
    return AdaptiveDimens.maxContentWidth()
}

/** Adaptive padding for screens (legacy — prefer AdaptiveDimens). */
object AdaptivePadding {
    @Composable fun horizontal(): Dp = AdaptiveDimens.horizontalPadding()
    @Composable fun vertical(): Dp = AdaptiveDimens.verticalPadding()
    @Composable fun card(): Dp = AdaptiveDimens.cardPadding()
    @Composable fun button(): Dp = AdaptiveDimens.iconButtonSize()
}

// ── Two-Pane Layout ───────────────────────────────────────────

/**
 * Two-pane layout that shows list + detail side by side on tablets,
 * and only the active pane on phones.
 */
@Composable
fun AdaptiveTwoPane(
    modifier: Modifier = Modifier,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    showListPane: Boolean = true,
    showDetailPane: Boolean = true,
    listPaneWeight: Float = 0.35f,
    detailPaneWeight: Float = 0.65f,
    divider: @Composable (() -> Unit)? = null
) {
    val info = LocalWindowAdaptiveInfo.current

    if (info.shouldUseTwoPane) {
        Row(modifier = modifier) {
            if (showListPane) {
                Box(
                    modifier = Modifier
                        .weight(listPaneWeight)
                        .fillMaxHeight()
                ) {
                    listPane()
                }
            }
            if (divider != null && showListPane && showDetailPane) {
                divider()
            } else if (showListPane && showDetailPane) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(androidx.compose.ui.graphics.Color.Transparent)
                )
            }
            if (showDetailPane) {
                Box(
                    modifier = Modifier
                        .weight(detailPaneWeight)
                        .fillMaxHeight()
                ) {
                    detailPane()
                }
            }
        }
    } else {
        // Single pane — show detail or list based on state
        Box(modifier = modifier) {
            if (showDetailPane && !showListPane) {
                detailPane()
            } else {
                listPane()
            }
        }
    }
}

// ── Centered Content Wrapper ──────────────────────────────────

/**
 * Wraps content with a max width constraint and centers it.
 * On phones, content fills the available width.
 */
@Composable
fun CenteredContent(
    maxWidth: Dp = AdaptiveDimens.maxContentWidth(),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = if (maxWidth != Dp.Unspecified) {
                Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxSize()
            } else {
                Modifier.fillMaxSize()
            },
            content = content
        )
    }
}

// ── Adaptive Navigation ───────────────────────────────────────

/**
 * Determines the appropriate navigation type for the current screen size.
 */
enum class NavigationType {
    BOTTOM_NAVIGATION,
    NAVIGATION_RAIL,
    PERMANENT_DRAWER
}

@Composable
fun adaptiveNavigationType(): NavigationType {
    val info = LocalWindowAdaptiveInfo.current
    return when {
        info.isExpanded -> NavigationType.NAVIGATION_RAIL
        info.isMedium && info.isLandscape -> NavigationType.NAVIGATION_RAIL
        else -> NavigationType.BOTTOM_NAVIGATION
    }
}

// ── WindowAdaptiveInfo Provider ───────────────────────────────

/**
 * Computes WindowSizeClass and provides WindowAdaptiveInfo to the tree.
 * Call this once at the top level (in NexaChatScreen or MainActivity).
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ProvideWindowAdaptiveInfo(
    content: @Composable () -> Unit
) {
    val windowSizeClass = calculateWindowSizeClass()
    val config = LocalConfiguration.current

    val adaptiveInfo = remember(windowSizeClass, config.screenWidthDp, config.screenHeightDp) {
        WindowAdaptiveInfo(
            widthSizeClass = windowSizeClass.widthSizeClass,
            heightSizeClass = windowSizeClass.heightSizeClass,
            screenWidthDp = config.screenWidthDp,
            screenHeightDp = config.screenHeightDp
        )
    }

    CompositionLocalProvider(LocalWindowAdaptiveInfo provides adaptiveInfo) {
        content()
    }
}

// ── Foldable Support Concept ──────────────────────────────────

/**
 * Represents foldable hinge information for future integration.
 * On foldable devices, this can be used to avoid placing interactive
 * content on the hinge area.
 */
data class FoldableHingeInfo(
    val isFolded: Boolean = false,
    val hingeWidthDp: Dp = 0.dp,
    val hingePosition: HingePosition = HingePosition.NONE
)

enum class HingePosition { NONE, LEFT, CENTER, RIGHT }

/**
 * Placeholder for foldable hinge detection.
 * Future: Integrate with WindowManager library for real hinge data.
 */
@Composable
fun rememberFoldableHingeInfo(): FoldableHingeInfo {
    val info = LocalWindowAdaptiveInfo.current
    // Concept: On medium-width devices in portrait, assume possible foldable
    return remember(info) {
        if (info.isMedium && !info.isLandscape) {
            FoldableHingeInfo(
                isFolded = false,
                hingeWidthDp = 0.dp,
                hingePosition = HingePosition.NONE
            )
        } else {
            FoldableHingeInfo()
        }
    }
}
