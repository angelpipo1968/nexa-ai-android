package com.nexa.ai.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Adaptive layout utilities for NEXA AI.
 * Ensures the app looks great on any screen size — phones, tablets, foldables.
 */

enum class DeviceType { PHONE, TABLET, FOLDABLE }

@Composable
fun rememberDeviceType(): DeviceType {
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp
    return when {
        widthDp >= 840 -> DeviceType.TABLET
        widthDp >= 600 -> DeviceType.FOLDABLE
        else -> DeviceType.PHONE
    }
}

/** Adaptive dimension that scales based on device type. */
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
    return when (rememberDeviceType()) {
        DeviceType.PHONE -> phone
        DeviceType.FOLDABLE -> foldable
        DeviceType.TABLET -> tablet
    }
}

/** Max content width for tablets — centers content on wide screens. */
@Composable
fun maxContentWidth(): Dp {
    return when (rememberDeviceType()) {
        DeviceType.PHONE -> Dp.Unspecified
        DeviceType.FOLDABLE -> 600.dp
        DeviceType.TABLET -> 720.dp
    }
}

/** Adaptive padding for screens. */
object AdaptivePadding {
    @Composable fun horizontal(): Dp = when (rememberDeviceType()) {
        DeviceType.PHONE -> 16.dp
        DeviceType.FOLDABLE -> 24.dp
        DeviceType.TABLET -> 32.dp
    }
    @Composable fun vertical(): Dp = when (rememberDeviceType()) {
        DeviceType.PHONE -> 8.dp
        DeviceType.FOLDABLE -> 12.dp
        DeviceType.TABLET -> 16.dp
    }
    @Composable fun card(): Dp = when (rememberDeviceType()) {
        DeviceType.PHONE -> 12.dp
        DeviceType.FOLDABLE -> 14.dp
        DeviceType.TABLET -> 16.dp
    }
    @Composable fun button(): Dp = when (rememberDeviceType()) {
        DeviceType.PHONE -> 36.dp
        DeviceType.FOLDABLE -> 40.dp
        DeviceType.TABLET -> 44.dp
    }
}
