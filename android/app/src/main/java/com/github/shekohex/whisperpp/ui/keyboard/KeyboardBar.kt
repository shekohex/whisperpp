package com.github.shekohex.whisperpp.ui.keyboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews

const val KEYBOARD_SETTINGS_BUTTON_TAG = "keyboard_settings_button"
const val KEYBOARD_COMMAND_BUTTON_TAG = "keyboard_command_button"

/** Bars at least this wide show the mode name in the pill as well as the language. */
private val ModeNameMinBarWidth = 420.dp
private val StartClusterMinWidth = 96.dp

/** Everything the bar renders; [KeyboardScreen] owns the state, the bar only draws and forwards. */
internal class KeyboardBarModel(
    val state: KeyboardState,
    val languageLabel: String,
    val modeState: KeyboardModeUiState?,
    val level: () -> Float,
    val recordingTimeMs: () -> Long,
    val gestureUi: GestureUi,
    val externalSendingBlocked: Boolean,
    val micPermissionMissing: Boolean,
    val showUndoAction: Boolean,
    val showEnhancementUndoAction: Boolean,
    val commandModeActive: Boolean,
    val showLongPressHint: Boolean,
    val tapToRecord: Boolean,
    val isStarting: Boolean,
)

internal class KeyboardBarActions(
    val onRecordIntent: (RecordIntent) -> Unit,
    val onGestureUi: (GestureUi) -> Unit,
    val onCancelAction: () -> Unit,
    val onDeleteAction: () -> Unit,
    val onUndoAction: () -> Unit,
    val onEnhancementUndoAction: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenContextSheet: () -> Unit,
    val onAllowMicPermission: () -> Unit,
    val onBlockedAction: () -> Unit,
    val onCommandToggle: () -> Unit,
    val onDismissHint: () -> Unit,
)

/**
 * The 72 dp floating bar: start cluster (settings + language·mode pill, or timer / trash), centre status
 * and end cluster (actions + record button). Every control keeps a 48 dp touch target; at 360 dp the
 * centre still fits "Ready" because the pill shows only the language there.
 */
@Composable
internal fun KeyboardBar(model: KeyboardBarModel, actions: KeyboardBarActions, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(KeyboardMetrics.baseHeight)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 4.dp,
    ) {
        BoxWithConstraints {
            val showModeName = maxWidth >= ModeNameMinBarWidth
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StartCluster(model, actions, showModeName)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CenterCluster(model, actions)
                }
                EndCluster(model, actions)
            }
        }
    }
}

@Composable
private fun StartCluster(model: KeyboardBarModel, actions: KeyboardBarActions, showModeName: Boolean) {
    Box(modifier = Modifier.widthIn(min = StartClusterMinWidth), contentAlignment = Alignment.CenterStart) {
        AnimatedContent(
            targetState = model.state,
            transitionSpec = { fadeIn() + slideInHorizontally() togetherWith fadeOut() + slideOutHorizontally() },
            label = "StartClusterTransition",
        ) { state ->
            when (state) {
                KeyboardState.Recording, KeyboardState.RecordingLocked -> {
                    Box(modifier = Modifier.padding(start = 12.dp)) { RecordingTimer(model.recordingTimeMs) }
                }
                KeyboardState.Paused, KeyboardState.PausedLocked -> {
                    IconButton(onClick = actions.onCancelAction) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.record_action_cancel),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                KeyboardState.Ready, KeyboardState.Transcribing, KeyboardState.Enhancing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = actions.onOpenSettings,
                            modifier = Modifier.testTag(KEYBOARD_SETTINGS_BUTTON_TAG),
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.btn_settings_hint),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        KeyboardContextPill(
                            languageLabel = model.languageLabel,
                            modeState = model.modeState,
                            showModeName = showModeName,
                            onClick = actions.onOpenContextSheet,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterCluster(model: KeyboardBarModel, actions: KeyboardBarActions) {
    val gestureUi = model.gestureUi
    AnimatedContent(
        targetState = centerContentFor(model.state, gestureUi),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "CenterContentTransition",
    ) { content ->
        when (content) {
            CenterContent.CancelHint -> CancelHint(
                progress = gestureUi.cancelProgress,
                committed = gestureUi.phase == GesturePhase.Cancelled,
            )
            CenterContent.LockHint -> LockHint(
                progress = gestureUi.lockProgress,
                locked = gestureUi.phase == GesturePhase.Locked,
            )
            CenterContent.Status -> when {
                model.state == KeyboardState.Paused || model.state == KeyboardState.PausedLocked -> PausedControlsCenter(
                    state = model.state,
                    recordingTimeMs = model.recordingTimeMs,
                    onResume = if (model.externalSendingBlocked) {
                        actions.onBlockedAction
                    } else {
                        { actions.onRecordIntent(RecordIntent.Resume) }
                    },
                )
                model.micPermissionMissing && model.state == KeyboardState.Ready ->
                    MicPermissionBanner(onAllow = actions.onAllowMicPermission)
                else -> StatusContent(model.state, model.level, model.recordingTimeMs)
            }
        }
    }
}

@Composable
private fun EndCluster(model: KeyboardBarModel, actions: KeyboardBarActions) {
    val state = model.state
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(visible = model.showEnhancementUndoAction) {
            AssistChip(
                onClick = actions.onEnhancementUndoAction,
                label = { Text(stringResource(R.string.undo)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(18.dp)) },
            )
        }
        AnimatedVisibility(visible = model.showUndoAction) {
            IconButton(onClick = actions.onUndoAction) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.undo),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(visible = state == KeyboardState.Ready) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = actions.onCommandToggle,
                    modifier = Modifier.testTag(KEYBOARD_COMMAND_BUTTON_TAG),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = stringResource(
                            if (model.commandModeActive) R.string.keyboard_command_exit else R.string.command_key_hint,
                        ),
                    )
                }
                BackspaceButton(actions.onDeleteAction)
            }
        }
        AnimatedVisibility(visible = state == KeyboardState.RecordingLocked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onCancelAction) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.record_action_cancel),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                IconButton(onClick = { actions.onRecordIntent(RecordIntent.Pause) }) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = stringResource(R.string.record_action_pause),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AnimatedVisibility(visible = state == KeyboardState.Transcribing || state == KeyboardState.Enhancing) {
            IconButton(onClick = actions.onCancelAction) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel_processing),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        MainActionButton(
            state = state,
            externalSendingBlocked = model.externalSendingBlocked,
            showLongPressHint = model.showLongPressHint,
            tapToRecord = model.tapToRecord,
            isStarting = model.isStarting,
            onIntent = actions.onRecordIntent,
            onUi = actions.onGestureUi,
            onDismissHint = actions.onDismissHint,
            onBlockedAction = actions.onBlockedAction,
        )
    }
}

internal fun previewBarActions() = KeyboardBarActions(
    onRecordIntent = {},
    onGestureUi = {},
    onCancelAction = {},
    onDeleteAction = {},
    onUndoAction = {},
    onEnhancementUndoAction = {},
    onOpenSettings = {},
    onOpenContextSheet = {},
    onAllowMicPermission = {},
    onBlockedAction = {},
    onCommandToggle = {},
    onDismissHint = {},
)

internal fun previewBarModel(
    state: KeyboardState = KeyboardState.Ready,
    modeState: KeyboardModeUiState? = null,
) = KeyboardBarModel(
    state = state,
    languageLabel = "EN",
    modeState = modeState,
    level = { 0.4f },
    recordingTimeMs = { 12_000L },
    gestureUi = GestureUi.Idle,
    externalSendingBlocked = false,
    micPermissionMissing = false,
    showUndoAction = false,
    showEnhancementUndoAction = false,
    commandModeActive = false,
    showLongPressHint = false,
    tapToRecord = true,
    isStarting = false,
)

@ThemePreviews
@Composable
private fun KeyboardBarPreview() {
    PreviewSurface {
        Box(modifier = Modifier.widthIn(max = 360.dp)) {
            KeyboardBar(model = previewBarModel(), actions = previewBarActions())
        }
    }
}
