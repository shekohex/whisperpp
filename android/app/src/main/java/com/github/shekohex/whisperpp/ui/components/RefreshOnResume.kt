package com.github.shekohex.whisperpp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Calls [onResume] on every `ON_RESUME`, e.g. to re-read permissions after returning from Settings. */
@Composable
fun RefreshOnResume(onResume: () -> Unit) {
    val currentOnResume by rememberUpdatedState(onResume)
    LifecycleResumeEffect(Unit) {
        currentOnResume()
        onPauseOrDispose { }
    }
}
