package com.github.shekohex.whisperpp.ui.keyboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.command.CommandStage
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESET_ID_TONE_REWRITE
import com.github.shekohex.whisperpp.data.presetById
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import com.github.shekohex.whisperpp.keyboard.isPaused
import com.github.shekohex.whisperpp.privacy.SecureFieldDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val PRIVACY_SAFETY_DESTINATION = "privacy_safety"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardScreen(
    state: KeyboardState,
    languageLabel: String,
    level: () -> Float,
    recordingTimeMs: () -> Long,
    showLongPressHint: Boolean,
    tapToRecord: Boolean = true,
    /** The session is opening the recorder: the button swallows presses (no second start / haptic). */
    isStarting: Boolean = false,
    undoAvailable: Boolean = false,
    undoQuickActionVisible: Boolean = false,
    enhancementNotice: EnhancementNoticeUiState? = null,
    enhancementUndoAvailable: Boolean = false,
    /** Record gesture, accessibility actions and the pause/resume buttons all go through here. */
    onRecordIntent: (RecordIntent) -> Unit,
    onCancelAction: () -> Unit,
    onDeleteAction: () -> Unit,
    onUndoAction: () -> Unit = {},
    onEnhancementUndoAction: () -> Unit = {},
    onDismissEnhancementNotice: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenSettingsDestination: (String) -> Unit = {},
    onLanguageClick: () -> Unit,
    onDismissHint: () -> Unit,
    externalSendBlockedReason: SecureFieldDetector.Reason? = null,
    externalSendBlockedByAppPolicy: Boolean = false,
    blockedPackageName: String? = null,
    showSecureFieldExplanation: Boolean = true,
    firstUseDisclosure: FirstUseDisclosureUiState? = null,
    onBlockedAction: () -> Unit = {},
    onDontShowSecureFieldExplanationAgain: () -> Unit = {},
    onFirstUseDisclosureContinue: () -> Unit = {},
    onFirstUseDisclosureCancel: () -> Unit = {},
    onFirstUseDisclosureOpenPrivacySafety: () -> Unit = {},
    micPermissionMissing: Boolean = false,
    onAllowMicPermission: () -> Unit = {},
    pendingCommitText: String? = null,
    onPendingCommitInsert: () -> Unit = {},
    onPendingCommitCopy: () -> Unit = {},
    onPendingCommitDiscard: () -> Unit = {},

    commandModeActive: Boolean = false,
    commandStage: CommandStage = CommandStage.WAITING,
    commandErrorMessage: String? = null,
    commandUndoAvailable: Boolean = false,
    commandUndoRemainingMs: Long = 0L,
    commandSelectedPresetId: String = TRANSFORM_PRESET_ID_TONE_REWRITE,
    commandClipboardPreview: String = "",
    commandClipboardCharCount: Int = 0,
    commandClipboardIsLarge: Boolean = false,
    commandClipboardUnavailableReason: String? = null,
    commandClipboardAttemptsRemaining: Int = 2,
    onCommandEnter: () -> Unit = {},
    onCommandCancel: () -> Unit = {},
    onCommandClipboardContinue: () -> Unit = {},
    onCommandClipboardRetry: () -> Unit = {},
    onCommandStopListening: () -> Unit = {},
    onCommandRetry: () -> Unit = {},
    onCommandUndo: () -> Unit = {},
    onCommandPresetSelected: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val overlayMaxHeight = keyboardOverlayMaxHeight()
    var gestureUi by remember { mutableStateOf(GestureUi.Idle) }
    val isPausedState = state.isPaused
    val externalSendingBlocked = externalSendBlockedReason != null || externalSendBlockedByAppPolicy
    val canShowBlockedExplanation = externalSendingBlocked && (externalSendBlockedByAppPolicy || showSecureFieldExplanation)
    val showUndoAction = undoAvailable && undoQuickActionVisible
    val showEnhancementUndoAction = enhancementUndoAvailable
    val copy = remember(externalSendBlockedReason, externalSendBlockedByAppPolicy, blockedPackageName) {
        blockedExplanationCopySpec(
            externalSendBlockedReason = externalSendBlockedReason,
            externalSendBlockedByAppPolicy = externalSendBlockedByAppPolicy,
            blockedPackageName = blockedPackageName,
        )
    }
    var showBlockedExplanation by remember { mutableStateOf(false) }
    var showBlockedExplanationFallback by remember { mutableStateOf(false) }
    val blockedExplanationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showCommandPresetPicker by remember { mutableStateOf(false) }

    val commandPreset = remember(commandSelectedPresetId) {
        presetById(commandSelectedPresetId)
    }
    val commandPresetLabel = commandPreset?.let { stringResource(it.titleRes) }
        ?: stringResource(R.string.command_preset_unknown)
    val showCommandOverlay = commandModeActive || commandStage != CommandStage.WAITING
    val commandStageLabel = when (commandStage) {
        CommandStage.WAITING -> stringResource(R.string.command_stage_waiting)
        CommandStage.CLIPBOARD_CONFIRM -> stringResource(R.string.command_stage_clipboard_confirm)
        CommandStage.LISTENING -> stringResource(R.string.command_stage_listening)
        CommandStage.PROCESSING -> stringResource(R.string.command_stage_processing)
        CommandStage.DONE -> stringResource(R.string.command_stage_done)
        CommandStage.ERROR -> stringResource(R.string.command_stage_error)
    }

    LaunchedEffect(externalSendingBlocked) {
        if (!externalSendingBlocked) {
            showBlockedExplanation = false
            showBlockedExplanationFallback = false
        }
    }

    LaunchedEffect(showBlockedExplanation, canShowBlockedExplanation) {
        if (!showBlockedExplanation || !canShowBlockedExplanation) {
            showBlockedExplanationFallback = false
            if (blockedExplanationSheetState.isVisible) {
                blockedExplanationSheetState.hide()
            }
            return@LaunchedEffect
        }

        showBlockedExplanationFallback = false
        val expanded = withTimeoutOrNull(300) {
            blockedExplanationSheetState.show()
            while (blockedExplanationSheetState.currentValue != SheetValue.Expanded) {
                delay(16)
            }
            true
        } == true

        if (!expanded) {
            blockedExplanationSheetState.hide()
            showBlockedExplanationFallback = true
        }
    }

    if (showBlockedExplanation && canShowBlockedExplanation && !showBlockedExplanationFallback) {
        ModalBottomSheet(
            onDismissRequest = { showBlockedExplanation = false },
            sheetState = blockedExplanationSheetState,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            BlockedExplanationContent(
                modifier = Modifier.heightIn(max = overlayMaxHeight),
                copy = copy,
                onOpenSettings = {
                    showBlockedExplanation = false
                    showBlockedExplanationFallback = false
                    onOpenSettingsDestination(PRIVACY_SAFETY_DESTINATION)
                },
                onClose = {
                    showBlockedExplanation = false
                    showBlockedExplanationFallback = false
                },
                onDontShowAgain = {
                    onDontShowSecureFieldExplanationAgain()
                    showBlockedExplanation = false
                    showBlockedExplanationFallback = false
                },
                showCloseAction = false,
            )
        }
    }

    if (showBlockedExplanation && canShowBlockedExplanation && showBlockedExplanationFallback) {
        KeyboardOverlayPopup(
            maxHeight = overlayMaxHeight,
            onDismissRequest = {
                showBlockedExplanation = false
                showBlockedExplanationFallback = false
            },
        ) {
            BlockedExplanationContent(
                copy = copy,
                onOpenSettings = {
                    showBlockedExplanation = false
                    showBlockedExplanationFallback = false
                    onOpenSettingsDestination(PRIVACY_SAFETY_DESTINATION)
                },
                onClose = {
                    showBlockedExplanation = false
                    showBlockedExplanationFallback = false
                },
                onDontShowAgain = {
                    onDontShowSecureFieldExplanationAgain()
                    showBlockedExplanation = false
                    showBlockedExplanationFallback = false
                },
                showCloseAction = true,
            )
        }
    }

    if (firstUseDisclosure != null) {
        KeyboardOverlayPopup(maxHeight = overlayMaxHeight, onDismissRequest = onFirstUseDisclosureCancel) {
            FirstUseDisclosureContent(
                disclosure = firstUseDisclosure,
                onContinue = onFirstUseDisclosureContinue,
                onOpenPrivacySafety = onFirstUseDisclosureOpenPrivacySafety,
                onCancel = onFirstUseDisclosureCancel,
            )
        }
    }

    if (commandModeActive && commandStage == CommandStage.CLIPBOARD_CONFIRM) {
        CommandClipboardSheet(
            overlayMaxHeight = overlayMaxHeight,
            unavailableReason = commandClipboardUnavailableReason,
            preview = commandClipboardPreview,
            charCount = commandClipboardCharCount,
            isLarge = commandClipboardIsLarge,
            attemptsRemaining = commandClipboardAttemptsRemaining,
            onContinue = onCommandClipboardContinue,
            onRetry = onCommandClipboardRetry,
            onCancel = onCommandCancel,
        )
    }

    if (showCommandPresetPicker && commandModeActive) {
        CommandPresetPickerSheet(
            overlayMaxHeight = overlayMaxHeight,
            selectedPresetId = commandSelectedPresetId,
            onSelect = { presetId ->
                onCommandPresetSelected(presetId)
                showCommandPresetPicker = false
            },
            onDismiss = { showCommandPresetPicker = false },
        )
    }

    val showBlockedExplanationAction: () -> Unit = {
        onBlockedAction()
        if (canShowBlockedExplanation) {
            val openedDetached = startBlockedExplanationActivity(
                context = context,
                externalSendBlockedReason = externalSendBlockedReason,
                externalSendBlockedByAppPolicy = externalSendBlockedByAppPolicy,
                blockedPackageName = blockedPackageName,
            )
            if (!openedDetached) {
                showBlockedExplanation = true
                showBlockedExplanationFallback = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        AnimatedVisibility(
            visible = enhancementNotice != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            enhancementNotice?.let { notice ->
                EnhancementNoticeBanner(notice = notice, onDismiss = onDismissEnhancementNotice)
            }
        }

        AnimatedVisibility(
            visible = pendingCommitText != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            pendingCommitText?.let { text ->
                PendingCommitBar(
                    text = text,
                    onInsert = onPendingCommitInsert,
                    onCopy = onPendingCommitCopy,
                    onDiscard = onPendingCommitDiscard,
                )
            }
        }

        AnimatedVisibility(
            visible = showCommandOverlay,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            CommandOverlayBar(
                overlayMaxHeight = overlayMaxHeight,
                stage = commandStage,
                stageLabel = commandStageLabel,
                errorMessage = commandErrorMessage,
                presetLabel = commandPresetLabel,
                undoAvailable = commandUndoAvailable,
                undoRemainingMs = commandUndoRemainingMs,
                onOpenPresetPicker = { showCommandPresetPicker = true },
                onUndo = onCommandUndo,
                onStopListening = onCommandStopListening,
                onRetry = onCommandRetry,
                onCancel = onCommandCancel,
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = KeyboardMetrics.barBottomGap)
                .height(KeyboardMetrics.baseHeight)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Cluster
                Box(modifier = Modifier.width(100.dp), contentAlignment = Alignment.CenterStart) {
                    AnimatedContent(
                        targetState = state,
                        transitionSpec = {
                            fadeIn() + slideInHorizontally() togetherWith fadeOut() + slideOutHorizontally()
                        },
                        label = "LeftClusterTransition"
                    ) { targetState ->
                        when (targetState) {
                            KeyboardState.Recording, KeyboardState.RecordingLocked -> {
                                RecordingTimer(recordingTimeMs)
                            }
                            KeyboardState.Paused, KeyboardState.PausedLocked -> {
                                IconButton(onClick = onCancelAction) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.record_action_cancel),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            KeyboardState.Ready, KeyboardState.Transcribing, KeyboardState.Enhancing -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = onOpenSettings) {
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = stringResource(R.string.btn_settings_hint),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        onClick = onLanguageClick,
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = languageLabel,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Center Status
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = centerContentFor(state, gestureUi),
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "CenterContentTransition"
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
                            CenterContent.Status -> if (isPausedState) {
                                PausedControlsCenter(
                                    state = state,
                                    recordingTimeMs = recordingTimeMs,
                                    onResume = if (externalSendingBlocked) {
                                        showBlockedExplanationAction
                                    } else {
                                        { onRecordIntent(RecordIntent.Resume) }
                                    },
                                )
                            } else if (micPermissionMissing && state == KeyboardState.Ready) {
                                MicPermissionBanner(onAllow = onAllowMicPermission)
                            } else {
                                StatusContent(state, level, recordingTimeMs)
                            }
                        }
                    }
                }

                // Right Cluster
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(visible = showEnhancementUndoAction) {
                        AssistChip(
                            onClick = onEnhancementUndoAction,
                            label = { Text(stringResource(R.string.undo)) },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                        )
                    }

                    AnimatedVisibility(visible = showUndoAction) {
                        IconButton(onClick = onUndoAction) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.undo),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    AnimatedVisibility(visible = state == KeyboardState.Ready) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (externalSendingBlocked) {
                                        showBlockedExplanationAction()
                                    } else if (commandModeActive) {
                                        onCommandCancel()
                                    } else {
                                        onCommandEnter()
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = stringResource(R.string.command_key_hint),
                                )
                            }
                            BackspaceButton(onDeleteAction)
                        }
                    }

                    AnimatedVisibility(visible = state == KeyboardState.RecordingLocked) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onCancelAction) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.record_action_cancel),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                            IconButton(onClick = { onRecordIntent(RecordIntent.Pause) }) {
                                Icon(
                                    Icons.Default.Pause,
                                    contentDescription = stringResource(R.string.record_action_pause),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = state == KeyboardState.Transcribing || state == KeyboardState.Enhancing) {
                        IconButton(onClick = onCancelAction) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cancel_processing),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    MainActionButton(
                        state = state,
                        externalSendingBlocked = externalSendingBlocked,
                        showLongPressHint = showLongPressHint,
                        tapToRecord = tapToRecord,
                        isStarting = isStarting,
                        onIntent = onRecordIntent,
                        onUi = { gestureUi = it },
                        onDismissHint = onDismissHint,
                        onBlockedAction = showBlockedExplanationAction,
                    )
                }
            }
        }
    }
}
