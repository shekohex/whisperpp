package com.github.shekohex.whisperpp.ime

import android.content.res.Configuration
import android.graphics.Color
import android.view.Window
import androidx.core.view.WindowCompat

/** Edge-to-edge input window: content draws behind a transparent navigation bar with matching icon contrast. */
internal fun Window.applyEdgeToEdge(configuration: Configuration) {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    navigationBarColor = Color.TRANSPARENT

    val isNightMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val insetsController = WindowCompat.getInsetsController(this, decorView)
    insetsController.isAppearanceLightNavigationBars = !isNightMode
}
