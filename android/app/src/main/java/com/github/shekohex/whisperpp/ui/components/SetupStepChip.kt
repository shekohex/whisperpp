package com.github.shekohex.whisperpp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Live done/pending chip for one setup step (onboarding pages and the Home checklist). */
@Composable
fun SetupStepChip(
    done: Boolean,
    doneText: String,
    pendingText: String,
    modifier: Modifier = Modifier,
) {
    StatusChip(
        text = if (done) doneText else pendingText,
        status = if (done) ChipStatus.Ok else ChipStatus.Warning,
        icon = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
        modifier = modifier,
    )
}
