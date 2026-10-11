package com.github.shekohex.whisperpp.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Single source of truth for keyboard sizing. Navigation-bar insets are applied once, at the root. */
object KeyboardMetrics {
    /** Height of the main keyboard bar in every state (Ready, Recording, Paused, Transcribing). */
    val baseHeight: Dp = 72.dp

    /** Minimum touch target for any interactive element on the bar. */
    val minTouchTarget: Dp = 56.dp

    /** Visual gap between the floating bar and the bottom edge (above the navigation-bar inset). */
    val barBottomGap: Dp = 15.dp

    /** Overlays (sheets, disclosures, command overlay) never take more than this share of the screen. */
    const val OVERLAY_MAX_SCREEN_FRACTION: Float = 0.6f
}

/**
 * Max height for keyboard overlays so the IME window never exceeds the available space. Uses the screen
 * height on purpose: the IME window wraps its content, so `LocalWindowInfo.containerSize` would be circular.
 */
@Suppress("ConfigurationScreenWidthHeight")
@Composable
fun keyboardOverlayMaxHeight(): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    return (screenHeightDp * KeyboardMetrics.OVERLAY_MAX_SCREEN_FRACTION).dp
}
