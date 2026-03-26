package com.github.shekohex.whisperpp.ui.keyboard

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.command.CommandStage
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESET_ID_TONE_REWRITE
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESETS
import com.github.shekohex.whisperpp.data.presetById
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import com.github.shekohex.whisperpp.keyboard.isLocked
import com.github.shekohex.whisperpp.keyboard.isPaused
import com.github.shekohex.whisperpp.keyboard.isRecording
import com.github.shekohex.whisperpp.privacy.SecureFieldDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val PRIVACY_SAFETY_DESTINATION = "privacy_safety"

data class FirstUseDisclosureUiState(
    val title: String,
    val dataSent: String,
    val endpointLines: List<String>,
    val contextLine: String,
)

enum class EnhancementNoticeStyle {
    INFO,
    ERROR,
}

data class EnhancementNoticeUiState(
    val message: String,
    val style: EnhancementNoticeStyle,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardScreen(
    state: KeyboardState,
    languageLabel: String,
    amplitude: Int,
    recordingTimeMs: Long,
    showLongPressHint: Boolean,
    undoAvailable: Boolean = false,
    undoQuickActionVisible: Boolean = false,
    enhancementNotice: EnhancementNoticeUiState? = null,
    enhancementUndoAvailable: Boolean = false,
    onMicAction: () -> Unit,
    onCancelAction: () -> Unit,
    onDiscardAction: () -> Unit,
    onSendAction: () -> Unit,
    onDeleteAction: () -> Unit,
    onUndoAction: () -> Unit = {},
    onEnhancementUndoAction: () -> Unit = {},
    onDismissEnhancementNotice: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenSettingsDestination: (String) -> Unit = {},
    onLanguageClick: () -> Unit,
    onDismissHint: () -> Unit,
    onLockAction: () -> Unit = {},
    onUnlockAction: () -> Unit = {},
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
    var swipeProgress by remember { mutableFloatStateOf(0f) }
    var swipeUpProgress by remember { mutableFloatStateOf(0f) }
    val isSwiping = swipeProgress > 0.05f
    val isSwipingUp = swipeUpProgress > 0.05f
    val isRecordingState = state.isRecording
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
    
    LaunchedEffect(state) {
        if (!isRecordingState) {
            swipeProgress = 0f
            swipeUpProgress = 0f
        }
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
            windowInsets = WindowInsets(0, 0, 0, 0),
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
                showCloseAction = false,
            )
        }
    }

    if (showBlockedExplanation && canShowBlockedExplanation && showBlockedExplanationFallback) {
        Popup(
            alignment = Alignment.BottomCenter,
            onDismissRequest = {
                showBlockedExplanation = false
                showBlockedExplanationFallback = false
            },
            properties = PopupProperties(focusable = true, clippingEnabled = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
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
    }

    if (firstUseDisclosure != null) {
        Popup(
            alignment = Alignment.BottomCenter,
            onDismissRequest = onFirstUseDisclosureCancel,
            properties = PopupProperties(focusable = true, clippingEnabled = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
            ) {
                FirstUseDisclosureContent(
                    disclosure = firstUseDisclosure,
                    onContinue = onFirstUseDisclosureContinue,
                    onOpenPrivacySafety = onFirstUseDisclosureOpenPrivacySafety,
                    onCancel = onFirstUseDisclosureCancel,
                )
            }
        }
    }

    if (commandModeActive && commandStage == CommandStage.CLIPBOARD_CONFIRM) {
        ModalBottomSheet(
            onDismissRequest = onCommandCancel,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.command_clipboard_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.command_clipboard_sheet_steps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val unavailable = commandClipboardUnavailableReason
                if (unavailable != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = unavailable,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.command_clipboard_sheet_recovery),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (commandClipboardPreview.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.command_clipboard_sheet_preview_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = commandClipboardPreview,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(R.string.command_clipboard_sheet_char_count, commandClipboardCharCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (commandClipboardIsLarge) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.command_clipboard_sheet_large_warning, commandClipboardCharCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.command_clipboard_sheet_attempts_remaining, commandClipboardAttemptsRemaining),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = onCommandClipboardContinue,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.command_clipboard_sheet_continue))
                }
                OutlinedButton(
                    onClick = onCommandClipboardRetry,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.command_clipboard_sheet_refresh))
                }
                TextButton(
                    onClick = onCommandCancel,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.command_cancel))
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showCommandPresetPicker && commandModeActive) {
        ModalBottomSheet(
            onDismissRequest = { showCommandPresetPicker = false },
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.command_preset_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.command_preset_picker_helper),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TRANSFORM_PRESETS.forEach { preset ->
                    val selected = preset.id == commandSelectedPresetId
                    ListItem(
                        headlineContent = { Text(stringResource(preset.titleRes)) },
                        supportingContent = { Text(stringResource(preset.descriptionRes)) },
                        leadingContent = {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onCommandPresetSelected(preset.id)
                                showCommandPresetPicker = false
                            },
                    )
                }

                TextButton(
                    onClick = { showCommandPresetPicker = false },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.command_close))
                }

                Spacer(Modifier.height(12.dp))
            }
        }
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

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = enhancementNotice != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            val notice = enhancementNotice
            if (notice != null) {
                val isError = notice.style == EnhancementNoticeStyle.ERROR
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clickable { onDismissEnhancementNotice() },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.Error else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = notice.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showCommandOverlay,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = commandStageLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val errorText = commandErrorMessage
                        if (!errorText.isNullOrBlank() && commandStage == CommandStage.ERROR) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = errorText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }

                    AssistChip(
                        onClick = { showCommandPresetPicker = true },
                        label = { Text(stringResource(R.string.command_preset_chip_label, commandPresetLabel)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )

                    if (commandUndoAvailable) {
                        AssistChip(
                            onClick = onCommandUndo,
                            label = { Text(stringResource(R.string.command_undo_chip_label, formatCommandUndoSeconds(commandUndoRemainingMs))) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }

                    when (commandStage) {
                        CommandStage.LISTENING -> {
                            FilledTonalButton(onClick = onCommandStopListening) {
                                Text(stringResource(R.string.command_stop_listening))
                            }
                        }
                        CommandStage.ERROR -> {
                            FilledTonalButton(onClick = onCommandRetry) {
                                Text(stringResource(R.string.command_retry))
                            }
                        }
                        else -> Unit
                    }

                    TextButton(onClick = onCommandCancel) {
                        Text(stringResource(R.string.command_cancel))
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 15.dp)
                .height(72.dp)
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
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSwiping && isRecordingState,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        PulsatingTrashIcon(swipeProgress)
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isSwiping || !isRecordingState,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
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
                                            null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                else -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = onOpenSettings) {
                                            Icon(
                                                Icons.Default.Settings,
                                                null,
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
                }

                // Center Status
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSwiping && isRecordingState,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        ShimmerText(
                            text = stringResource(R.string.swipe_to_discard),
                            progress = swipeProgress
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSwipingUp && state == KeyboardState.Recording && !isSwiping,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        ShimmerLockText(progress = swipeUpProgress)
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isSwiping && !isSwipingUp || !isRecordingState,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        if (isPausedState) {
                            PausedControlsCenter(
                                state = state,
                                recordingTimeMs = recordingTimeMs,
                                onResume = if (externalSendingBlocked) showBlockedExplanationAction else onMicAction,
                            )
                        } else {
                            StatusContent(state, amplitude, recordingTimeMs)
                        }
                    }
                }

                // Right Cluster
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(visible = showEnhancementUndoAction) {
                        AssistChip(
                            onClick = onEnhancementUndoAction,
                            label = { Text("Undo") },
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
                                null,
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

                    AnimatedVisibility(visible = state == KeyboardState.Transcribing || state == KeyboardState.SmartFixing) {
                        IconButton(onClick = onCancelAction) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    if (isPausedState) {
                        FilledIconButton(
                            onClick = if (externalSendingBlocked) showBlockedExplanationAction else onSendAction,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                            )
                        }
                    } else {
                        MainActionButton(
                            state = state,
                            externalSendingBlocked = externalSendingBlocked,
                            showLongPressHint = showLongPressHint,
                            onSwipeProgress = { swipeProgress = it },
                            onSwipeUpProgress = { swipeUpProgress = it },
                            onMicAction = onMicAction,
                            onDiscardAction = onDiscardAction,
                            onSendAction = onSendAction,
                            onDismissHint = onDismissHint,
                            onLockAction = onLockAction,
                            onUnlockAction = onUnlockAction,
                            onBlockedAction = showBlockedExplanationAction,
                        )
                    }
                }
            }
        }
    }
}

private fun startBlockedExplanationActivity(
    context: Context,
    externalSendBlockedReason: SecureFieldDetector.Reason?,
    externalSendBlockedByAppPolicy: Boolean,
    blockedPackageName: String?,
): Boolean {
    val intent = Intent(context, BlockedExplanationActivity::class.java)
        .putExtra(BlockedExplanationActivity.EXTRA_EXTERNAL_SEND_BLOCKED_BY_APP_POLICY, externalSendBlockedByAppPolicy)
        .putExtra(BlockedExplanationActivity.EXTRA_EXTERNAL_SEND_BLOCKED_PACKAGE_NAME, blockedPackageName)
        .putExtra(BlockedExplanationActivity.EXTRA_EXTERNAL_SEND_BLOCKED_REASON, externalSendBlockedReason?.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}

@Composable
internal fun BlockedExplanationContent(
    copy: BlockedExplanationCopySpec,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    onDontShowAgain: () -> Unit,
    showCloseAction: Boolean,
    modifier: Modifier = Modifier,
) {
    val reasonText = copy.reasonArg?.let { arg ->
        stringResource(copy.reasonRes, arg)
    } ?: stringResource(copy.reasonRes)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(copy.titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (showCloseAction) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                    )
                }
            }
        }
        Text(
            text = stringResource(copy.descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = reasonText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.secure_field_sheet_open_settings))
        }
        if (copy.showDontShowAgain) {
            TextButton(
                onClick = onDontShowAgain,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.secure_field_sheet_dont_show_again))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun FirstUseDisclosureContent(
    disclosure: FirstUseDisclosureUiState,
    onContinue: () -> Unit,
    onOpenPrivacySafety: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = disclosure.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = disclosure.dataSent,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        disclosure.endpointLines.forEach { endpointLine ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = endpointLine,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
        Text(
            text = disclosure.contextLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
        }
        OutlinedButton(
            onClick = onOpenPrivacySafety,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open Privacy & Safety")
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Cancel")
        }
        Spacer(Modifier.height(12.dp))
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    val ms = (millis % 1000) / 10
    return "%02d:%02d.%02d".format(mins, secs, ms)
}

private fun formatCommandUndoSeconds(remainingMs: Long): Int {
    if (remainingMs <= 0L) return 0
    val seconds = (remainingMs + 999L) / 1000L
    return seconds.toInt().coerceAtLeast(0)
}

@Composable
fun RecordingTimer(millis: Long) {
    val infiniteTransition = rememberInfiniteTransition(label = "RecDot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotAlpha"
    )
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = dotAlpha))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = formatTime(millis),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PausedControlsCenter(
    state: KeyboardState,
    recordingTimeMs: Long,
    onResume: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (state.isLocked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                Icons.Default.Pause,
                contentDescription = null,
                tint = Color(0xFFFFD600),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = formatTime(recordingTimeMs),
                color = Color(0xFFFFD600),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        FilledTonalIconButton(
            onClick = onResume,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
            )
        }
    }
}

@Composable
fun StatusContent(state: KeyboardState, amplitude: Int, recordingTimeMs: Long) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
        },
        label = "KeyboardStateTransition"
    ) { targetState ->
        when (targetState) {
            KeyboardState.Ready -> {
                Text(
                    stringResource(R.string.input_ready),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            KeyboardState.Recording -> {
                VoiceWaveform(amplitude)
            }
            KeyboardState.RecordingLocked -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    VoiceWaveform(amplitude)
                }
            }
            KeyboardState.Paused -> {
                Text(
                    text = formatTime(recordingTimeMs),
                    color = Color(0xFFFFD600),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.animateAlphaLoop()
                )
            }
            KeyboardState.PausedLocked -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatTime(recordingTimeMs),
                        color = Color(0xFFFFD600),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.animateAlphaLoop()
                    )
                }
            }
            KeyboardState.Transcribing -> {
                TranscribingStatus()
            }
            KeyboardState.SmartFixing -> {
                WhimsicalStatus()
            }
        }
    }
}

@Composable
fun TranscribingStatus() {
    val infiniteTransition = rememberInfiniteTransition(label = "TranscribingShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "TranscribingShimmerOffset"
    )
    
    val baseColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.primary
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(shimmerOffset * 400f - 100f, 0f),
        end = Offset(shimmerOffset * 400f + 100f, 0f)
    )
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Transcribing...",
            style = MaterialTheme.typography.bodyMedium.copy(brush = brush)
        )
    }
}

@Composable
fun VoiceWaveform(amplitude: Int) {
    val bars = 5
    val multipliers = listOf(0.5f, 0.8f, 1.0f, 0.8f, 0.5f)
    
    val normalized = remember(amplitude) {
        val amp = amplitude.coerceIn(10, 25000).toFloat()
        (kotlin.math.log10(amp) - 1f) / (4.398f - 1f)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(bars) { index ->
            val height by animateDpAsState(
                targetValue = (4 + (20 * normalized * multipliers[index])).dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "WaveformBarHeight"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun PulsatingTrashIcon(progress: Float) {
    val pulseDuration = (600 - (progress * 400)).toInt().coerceAtLeast(160)
    
    val infiniteTransition = rememberInfiniteTransition(label = "TrashPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f + (progress * 0.2f),
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TrashScale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TrashAlpha"
    )
    
    Icon(
        Icons.Default.Delete,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error.copy(alpha = alpha),
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    )
}

@Composable
fun MainActionButton(
    state: KeyboardState,
    externalSendingBlocked: Boolean,
    showLongPressHint: Boolean,
    onSwipeProgress: (Float) -> Unit,
    onSwipeUpProgress: (Float) -> Unit,
    onMicAction: () -> Unit,
    onDiscardAction: () -> Unit,
    onSendAction: () -> Unit,
    onDismissHint: () -> Unit,
    onLockAction: () -> Unit,
    onUnlockAction: () -> Unit,
    onBlockedAction: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val currentState by rememberUpdatedState(state)
    val swipeThreshold = 420f
    val swipeUpThreshold = 220f
    
    LaunchedEffect(showLongPressHint) {
        if (showLongPressHint) {
            delay(2500)
            onDismissHint()
        }
    }
    
    val size by animateDpAsState(
        targetValue = when (state) {
            KeyboardState.Recording -> 64.dp
            KeyboardState.RecordingLocked -> 56.dp
            else -> 48.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ButtonSize"
    )

    val containerColor by animateColorAsState(
        when {
            externalSendingBlocked -> MaterialTheme.colorScheme.surfaceVariant
            state == KeyboardState.Recording -> MaterialTheme.colorScheme.error
            state == KeyboardState.RecordingLocked -> MaterialTheme.colorScheme.tertiary
            state == KeyboardState.Paused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        label = "ContainerColor"
    )

    val contentColor by animateColorAsState(
        when {
            externalSendingBlocked -> MaterialTheme.colorScheme.onSurfaceVariant
            state == KeyboardState.Recording -> MaterialTheme.colorScheme.onError
            state == KeyboardState.RecordingLocked -> MaterialTheme.colorScheme.onTertiary
            state == KeyboardState.Paused -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        label = "ContentColor"
    )

    val icon = when {
        externalSendingBlocked -> Icons.Default.Lock
        state == KeyboardState.Paused -> Icons.AutoMirrored.Filled.Send
        state == KeyboardState.RecordingLocked -> Icons.Default.Lock
        else -> Icons.Default.Mic
    }

    val interactionModifier = if (externalSendingBlocked) {
        Modifier.clickable(onClick = onBlockedAction)
    } else {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                val stateAtDown = currentState

                if (stateAtDown == KeyboardState.Ready) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMicAction()

                    var currentDragX = 0f
                    var currentDragY = 0f
                    var didHapticAtThreshold = false
                    var didHapticAtLockThreshold = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        if (event.type == PointerEventType.Move) {
                            val dragDelta = change.positionChange()
                            currentDragX += dragDelta.x
                            currentDragY += dragDelta.y

                            val leftProgress = ((-currentDragX) / swipeThreshold).coerceIn(0f, 1f)
                            val upProgress = ((-currentDragY) / swipeUpThreshold).coerceIn(0f, 1f)

                            if (leftProgress > upProgress) {
                                onSwipeProgress(leftProgress)
                                onSwipeUpProgress(0f)

                                if (leftProgress >= 1f && !didHapticAtThreshold) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    didHapticAtThreshold = true
                                } else if (leftProgress < 0.9f) {
                                    didHapticAtThreshold = false
                                }
                            } else if (upProgress > 0.05f) {
                                onSwipeUpProgress(upProgress)
                                onSwipeProgress(0f)

                                if (upProgress >= 1f && !didHapticAtLockThreshold) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    didHapticAtLockThreshold = true
                                } else if (upProgress < 0.9f) {
                                    didHapticAtLockThreshold = false
                                }
                            } else {
                                onSwipeProgress(0f)
                                onSwipeUpProgress(0f)
                            }
                        }

                        if (!change.pressed) {
                            val finalLeftProgress = ((-currentDragX) / swipeThreshold).coerceIn(0f, 1f)
                            val finalUpProgress = ((-currentDragY) / swipeUpThreshold).coerceIn(0f, 1f)

                            if (finalLeftProgress >= 1f && finalLeftProgress > finalUpProgress) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDiscardAction()
                            } else if (finalUpProgress >= 1f && finalUpProgress > finalLeftProgress) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLockAction()
                            } else {
                                onMicAction()
                            }
                            onSwipeProgress(0f)
                            onSwipeUpProgress(0f)
                            break
                        }
                    }
                } else if (stateAtDown == KeyboardState.Recording) {
                    var currentDragX = 0f
                    var currentDragY = 0f
                    var didHapticAtThreshold = false
                    var didHapticAtLockThreshold = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        if (event.type == PointerEventType.Move) {
                            val dragDelta = change.positionChange()
                            currentDragX += dragDelta.x
                            currentDragY += dragDelta.y

                            val leftProgress = ((-currentDragX) / swipeThreshold).coerceIn(0f, 1f)
                            val upProgress = ((-currentDragY) / swipeUpThreshold).coerceIn(0f, 1f)

                            if (leftProgress > upProgress) {
                                onSwipeProgress(leftProgress)
                                onSwipeUpProgress(0f)

                                if (leftProgress >= 1f && !didHapticAtThreshold) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    didHapticAtThreshold = true
                                } else if (leftProgress < 0.9f) {
                                    didHapticAtThreshold = false
                                }
                            } else if (upProgress > 0.05f) {
                                onSwipeUpProgress(upProgress)
                                onSwipeProgress(0f)

                                if (upProgress >= 1f && !didHapticAtLockThreshold) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    didHapticAtLockThreshold = true
                                } else if (upProgress < 0.9f) {
                                    didHapticAtLockThreshold = false
                                }
                            } else {
                                onSwipeProgress(0f)
                                onSwipeUpProgress(0f)
                            }
                        }

                        if (!change.pressed) {
                            val finalLeftProgress = ((-currentDragX) / swipeThreshold).coerceIn(0f, 1f)
                            val finalUpProgress = ((-currentDragY) / swipeUpThreshold).coerceIn(0f, 1f)

                            if (finalLeftProgress >= 1f && finalLeftProgress > finalUpProgress) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDiscardAction()
                            } else if (finalUpProgress >= 1f && finalUpProgress > finalLeftProgress) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLockAction()
                            } else {
                                onMicAction()
                            }
                            onSwipeProgress(0f)
                            onSwipeUpProgress(0f)
                            break
                        }
                    }
                } else if (stateAtDown == KeyboardState.RecordingLocked) {
                    var currentDragX = 0f
                    var didHapticAtThreshold = false

                    val up = withTimeoutOrNull(400) {
                        waitForUpOrCancellation()
                    }

                    if (up != null) {
                        onUnlockAction()
                    } else {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (event.type == PointerEventType.Move) {
                                val dragDelta = change.positionChange()
                                currentDragX += dragDelta.x

                                val leftProgress = ((-currentDragX) / swipeThreshold).coerceIn(0f, 1f)
                                onSwipeProgress(leftProgress)

                                if (leftProgress >= 1f && !didHapticAtThreshold) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    didHapticAtThreshold = true
                                } else if (leftProgress < 0.9f) {
                                    didHapticAtThreshold = false
                                }
                            }

                            if (!change.pressed) {
                                val finalProgress = ((-currentDragX) / swipeThreshold).coerceIn(0f, 1f)
                                if (finalProgress >= 1f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDiscardAction()
                                }
                                onSwipeProgress(0f)
                                break
                            }
                        }
                    }
                } else if (stateAtDown == KeyboardState.Paused) {
                    val up = withTimeoutOrNull(400) {
                        waitForUpOrCancellation()
                    }
                    if (up != null) {
                        onSendAction()
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMicAction()

                        var currentDragX = 0f
                        var currentDragY = 0f
                        var didHapticAtThreshold = false
                        var didHapticAtLockThreshold = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (event.type == PointerEventType.Move) {
                                val dragDelta = change.positionChange()
                                currentDragX += dragDelta.x
                                currentDragY += dragDelta.y

                                val leftProgress = ((-currentDragX) / swipeThreshold).coerceIn(0f, 1f)
                                val upProgress = ((-currentDragY) / swipeUpThreshold).coerceIn(0f, 1f)

                                if (leftProgress > upProgress) {
                                    onSwipeProgress(leftProgress)
                                    onSwipeUpProgress(0f)

                                    if (leftProgress >= 1f && !didHapticAtThreshold) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        didHapticAtThreshold = true
                                    } else if (leftProgress < 0.9f) {
                                        didHapticAtThreshold = false
                                    }
                                } else if (upProgress > 0.05f) {
                                    onSwipeUpProgress(upProgress)
                                    onSwipeProgress(0f)

                                    if (upProgress >= 1f && !didHapticAtLockThreshold) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        didHapticAtLockThreshold = true
                                    } else if (upProgress < 0.9f) {
                                        didHapticAtLockThreshold = false
                                    }
                                } else {
                                    onSwipeProgress(0f)
                                    onSwipeUpProgress(0f)
                                }
                            }

                            if (!change.pressed) {
                                val finalLeftProgress = ((-currentDragX) / swipeThreshold).coerceIn(0f, 1f)
                                val finalUpProgress = ((-currentDragY) / swipeUpThreshold).coerceIn(0f, 1f)

                                if (finalLeftProgress >= 1f && finalLeftProgress > finalUpProgress) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDiscardAction()
                                } else if (finalUpProgress >= 1f && finalUpProgress > finalLeftProgress) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLockAction()
                                } else {
                                    onMicAction()
                                }
                                onSwipeProgress(0f)
                                onSwipeUpProgress(0f)
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showLongPressHint && !externalSendingBlocked) {
            Popup(
                alignment = Alignment.BottomCenter,
                offset = androidx.compose.ui.unit.IntOffset(-110, -160),
                properties = PopupProperties(clippingEnabled = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = stringResource(R.string.long_press_hint),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
        
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(containerColor)
                .then(interactionModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(when (state) {
                    KeyboardState.Recording -> 32.dp
                    KeyboardState.RecordingLocked -> 28.dp
                    else -> 24.dp
                })
            )
        }
    }
}

@Composable
fun BackspaceButton(onDeleteAction: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            onDeleteAction()
            delay(500L)
            var currentDelay = 100L
            while (isPressed) {
                onDeleteAction()
                delay(currentDelay)
                currentDelay = (currentDelay * 0.9f).toLong().coerceAtLeast(40L)
            }
        }
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    isPressed = true
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        isPressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Backspace,
            contentDescription = stringResource(R.string.backspace_button_hint),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun WhimsicalStatus() {
    val whimsicalWords = listOf(
        "Polishing", "Refining", "Synthesizing", "De-umm-ing", "Structuring",
        "Articulating", "Grammarizing", "Decrypting", "Unscrambling", "Enhancing",
        "Harmonizing", "Orchestrating", "Decoding", "Calibrating", "Interpreting",
        "Clarifying", "Perfecting", "Stylizing", "Flowing", "Connecting",
        "Analyzing", "Processing", "Distilling", "Capturing", "Translating",
        "Echoing", "Vocalizing", "Resonating", "Balancing", "Filtering",
        "Reconstructing", "Smoothing", "Fine-tuning", "Adapting", "Reshaping",
        "Aligning", "Synchronizing", "Optimizing", "Gleaning", "Extracting",
        "Transcribing", "Enunciating", "Projecting", "Symphonizing", "Detecting",
        "Listening", "Composing", "Crafting", "Mending", "Rectifying",
        "Mapping", "Weaving", "Sculpting", "Whispering", "Thinking",
        "Untangling", "Crystallizing", "Illuminating", "Channeling", "Parsing",
        "Braiding", "Conjuring", "Manifesting", "Transmuting", "Awakening",
        "Brewing", "Curating", "Deciphering", "Elevating", "Forging",
        "Germinating", "Harvesting", "Infusing", "Kindling", "Layering",
        "Marinating", "Nurturing", "Orchestrating", "Percolating", "Quickening",
        "Radiating", "Simmering", "Tempering", "Unveiling", "Ventilating",
        "Wrangling", "Yielding", "Zeroing", "Assembling", "Blending",
        "Condensing", "Digesting", "Embroidering", "Funneling", "Galvanizing"
    )
    val glyphs = listOf("·", "✻", "✽", "✶", "✳", "✢")
    
    var word by remember { mutableStateOf(whimsicalWords.random()) }
    var glyphIndex by remember { mutableIntStateOf(0) }
    val glyph = glyphs[glyphIndex]
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(word) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2500)
            word = whimsicalWords.random()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            glyphIndex = (glyphIndex + 1) % glyphs.size
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "WhimsicalShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WhimsicalShimmerOffset"
    )
    
    val baseColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(shimmerOffset * 400f - 100f, 0f),
        end = Offset(shimmerOffset * 400f + 100f, 0f)
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(glyph, color = MaterialTheme.colorScheme.tertiary, fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = word,
            style = MaterialTheme.typography.bodyMedium.copy(brush = brush)
        )
    }
}

fun Modifier.animateAlphaLoop(): Modifier = this.composed {
    val infiniteTransition = rememberInfiniteTransition(label = "AlphaLoop")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaValue"
    )
    this.then(Modifier.graphicsLayer { this.alpha = alpha })
}

@Composable
fun ShimmerText(text: String, progress: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "Shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerOffset"
    )
    
    val baseColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
    val highlightColor = MaterialTheme.colorScheme.error
    
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(shimmerOffset * 500f - 100f, 0f),
        end = Offset(shimmerOffset * 500f + 100f, 0f)
    )
    
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
            brush = brush
        ),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.graphicsLayer {
            alpha = 0.7f + (progress * 0.3f)
        }
    )
}

@Composable
fun ShimmerLockText(progress: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "LockShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "LockShimmerOffset"
    )
    
    val baseColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
    val highlightColor = MaterialTheme.colorScheme.tertiary
    
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(shimmerOffset * 500f - 100f, 0f),
        end = Offset(shimmerOffset * 500f + 100f, 0f)
    )
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.swipe_to_lock),
            style = MaterialTheme.typography.titleMedium.copy(brush = brush),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer {
                alpha = 0.7f + (progress * 0.3f)
            }
        )
    }
}
