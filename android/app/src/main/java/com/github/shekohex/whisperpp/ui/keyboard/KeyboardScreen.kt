package com.github.shekohex.whisperpp.ui.keyboard

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.command.CommandStage
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESET_ID_TONE_REWRITE
import com.github.shekohex.whisperpp.data.presetById
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import com.github.shekohex.whisperpp.privacy.SecureFieldDetector
import com.github.shekohex.whisperpp.ui.navigation.SettingsDeepLinks

private const val PRIVACY_SAFETY_DESTINATION = "privacy_safety"

/** In-keyboard sheets, highest priority first; at most one is shown at a time. */
private enum class KeyboardSheet { Disclosure, CancelConfirmation, Blocked, Clipboard, Presets, LanguageMode }

@Composable
fun KeyboardScreen(
    state: KeyboardState,
    languageLabel: String,
    level: () -> Float,
    recordingTimeMs: () -> Long,
    showLongPressHint: Boolean,
    tapToRecord: Boolean = true,
    /** Settings: the command-mode button and the mode icon are optional so the bar stays minimal. */
    commandModeEnabled: Boolean = false,
    showModeOnKeyboard: Boolean = true,
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
    /** Pickable dictation languages and the stored choice ("auto" or a language code). */
    languageOptions: List<KeyboardLanguageOption> = emptyList(),
    selectedLanguage: String = "auto",
    onLanguageSelected: (String) -> Unit = {},
    onDismissHint: () -> Unit,
    /** Mode shown in the language·mode pill; null shows the language only (modes not read yet). */
    modeState: KeyboardModeUiState? = null,
    onModeSelected: (String) -> Unit = {},
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
    /** "Discard this recording?" is open (in-keyboard sheet, H-14). */
    cancelConfirmationVisible: Boolean = false,
    onCancelConfirmed: () -> Unit = {},
    onCancelConfirmDismissed: () -> Unit = {},
    /** Receives the Back action of the topmost in-keyboard sheet, or null when none is open. */
    onOverlayBackChanged: ((() -> Unit)?) -> Unit = {},

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
    val externalSendingBlocked = externalSendBlockedReason != null || externalSendBlockedByAppPolicy
    val canShowBlockedExplanation = externalSendingBlocked && (externalSendBlockedByAppPolicy || showSecureFieldExplanation)
    val copy = remember(externalSendBlockedReason, externalSendBlockedByAppPolicy, blockedPackageName) {
        blockedExplanationCopySpec(
            externalSendBlockedReason = externalSendBlockedReason,
            externalSendBlockedByAppPolicy = externalSendBlockedByAppPolicy,
            blockedPackageName = blockedPackageName,
        )
    }
    var showBlockedExplanation by remember { mutableStateOf(false) }
    var showCommandPresetPicker by remember { mutableStateOf(false) }
    var showContextSheet by remember { mutableStateOf(false) }

    val commandPreset = remember(commandSelectedPresetId) { presetById(commandSelectedPresetId) }
    val commandPresetLabel = commandPreset?.let { stringResource(it.titleRes) }
        ?: stringResource(R.string.command_preset_unknown)
    val showCommandOverlay = commandModeActive || commandStage != CommandStage.WAITING
    val commandStageLabel = stringResource(commandStage.labelRes())

    LaunchedEffect(externalSendingBlocked) {
        if (!externalSendingBlocked) showBlockedExplanation = false
    }

    val activeSheet = when {
        firstUseDisclosure != null -> KeyboardSheet.Disclosure
        cancelConfirmationVisible -> KeyboardSheet.CancelConfirmation
        showBlockedExplanation && canShowBlockedExplanation -> KeyboardSheet.Blocked
        commandModeActive && commandStage == CommandStage.CLIPBOARD_CONFIRM -> KeyboardSheet.Clipboard
        showCommandPresetPicker && commandModeActive -> KeyboardSheet.Presets
        showContextSheet && state == KeyboardState.Ready -> KeyboardSheet.LanguageMode
        else -> null
    }
    val dismissActiveSheet: () -> Unit = {
        when (activeSheet) {
            KeyboardSheet.Disclosure -> onFirstUseDisclosureCancel()
            KeyboardSheet.CancelConfirmation -> onCancelConfirmDismissed()
            KeyboardSheet.Blocked -> showBlockedExplanation = false
            KeyboardSheet.Clipboard -> onCommandCancel()
            KeyboardSheet.Presets -> showCommandPresetPicker = false
            KeyboardSheet.LanguageMode -> showContextSheet = false
            null -> Unit
        }
    }
    val currentDismiss by rememberUpdatedState(dismissActiveSheet)
    val currentOnOverlayBackChanged by rememberUpdatedState(onOverlayBackChanged)
    DisposableEffect(activeSheet != null) {
        currentOnOverlayBackChanged(if (activeSheet != null) ({ currentDismiss() }) else null)
        onDispose { currentOnOverlayBackChanged(null) }
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
            if (!openedDetached) showBlockedExplanation = true
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

        AnimatedVisibility(
            visible = showLongPressHint && !externalSendingBlocked,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LongPressHintBanner()
        }

        when (activeSheet) {
            KeyboardSheet.Disclosure -> firstUseDisclosure?.let { disclosure ->
                FirstUseDisclosureSheet(
                    disclosure = disclosure,
                    maxHeight = overlayMaxHeight,
                    onContinue = onFirstUseDisclosureContinue,
                    onOpenPrivacySafety = onFirstUseDisclosureOpenPrivacySafety,
                    onCancel = onFirstUseDisclosureCancel,
                )
            }
            KeyboardSheet.CancelConfirmation -> CancelConfirmationSheet(
                maxHeight = overlayMaxHeight,
                onConfirm = onCancelConfirmed,
                onDismiss = onCancelConfirmDismissed,
            )
            KeyboardSheet.Blocked -> BlockedExplanationSheet(
                copy = copy,
                maxHeight = overlayMaxHeight,
                onOpenSettings = {
                    showBlockedExplanation = false
                    onOpenSettingsDestination(PRIVACY_SAFETY_DESTINATION)
                },
                onClose = { showBlockedExplanation = false },
                onDontShowAgain = {
                    onDontShowSecureFieldExplanationAgain()
                    showBlockedExplanation = false
                },
            )
            KeyboardSheet.Clipboard -> CommandClipboardSheet(
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
            KeyboardSheet.Presets -> CommandPresetPickerSheet(
                overlayMaxHeight = overlayMaxHeight,
                selectedPresetId = commandSelectedPresetId,
                onSelect = { presetId ->
                    onCommandPresetSelected(presetId)
                    showCommandPresetPicker = false
                },
                onDismiss = { showCommandPresetPicker = false },
            )
            KeyboardSheet.LanguageMode -> KeyboardLanguageModeSheet(
                languages = languageOptions,
                selectedLanguage = selectedLanguage,
                modeState = modeState,
                maxHeight = overlayMaxHeight,
                onLanguageSelected = { code ->
                    onLanguageSelected(code)
                    showContextSheet = false
                },
                onModeSelected = { modeId ->
                    onModeSelected(modeId)
                    showContextSheet = false
                },
                onManageModes = {
                    showContextSheet = false
                    onOpenSettingsDestination(SettingsDeepLinks.MODES)
                },
                onDismiss = { showContextSheet = false },
            )
            null -> Unit
        }

        KeyboardBar(
            model = KeyboardBarModel(
                state = state,
                languageLabel = languageLabel,
                modeState = modeState,
                level = level,
                recordingTimeMs = recordingTimeMs,
                gestureUi = gestureUi,
                externalSendingBlocked = externalSendingBlocked,
                micPermissionMissing = micPermissionMissing,
                showUndoAction = undoAvailable && undoQuickActionVisible,
                showEnhancementUndoAction = enhancementUndoAvailable,
                commandModeActive = commandModeActive,
                showLongPressHint = showLongPressHint,
                tapToRecord = tapToRecord,
                isStarting = isStarting,
                commandButtonVisible = commandModeEnabled,
                showModeIcon = showModeOnKeyboard,
            ),
            actions = KeyboardBarActions(
                onRecordIntent = onRecordIntent,
                onGestureUi = { gestureUi = it },
                onCancelAction = onCancelAction,
                onDeleteAction = onDeleteAction,
                onUndoAction = onUndoAction,
                onEnhancementUndoAction = onEnhancementUndoAction,
                onOpenSettings = onOpenSettings,
                onOpenContextSheet = { showContextSheet = !showContextSheet },
                onAllowMicPermission = onAllowMicPermission,
                onBlockedAction = showBlockedExplanationAction,
                onCommandToggle = {
                    when {
                        externalSendingBlocked -> showBlockedExplanationAction()
                        commandModeActive -> onCommandCancel()
                        else -> onCommandEnter()
                    }
                },
                onDismissHint = onDismissHint,
            ),
            modifier = Modifier.padding(bottom = keyboardBarBottomGap()),
        )
    }
}

@StringRes
private fun CommandStage.labelRes(): Int = when (this) {
    CommandStage.WAITING -> R.string.command_stage_waiting
    CommandStage.CLIPBOARD_CONFIRM -> R.string.command_stage_clipboard_confirm
    CommandStage.LISTENING -> R.string.command_stage_listening
    CommandStage.PROCESSING -> R.string.command_stage_processing
    CommandStage.DONE -> R.string.command_stage_done
    CommandStage.ERROR -> R.string.command_stage_error
}
