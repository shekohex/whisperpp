package com.github.shekohex.whisperpp.ime

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.shekohex.whisperpp.domain.dictation.DictationSession
import com.github.shekohex.whisperpp.ui.keyboard.KeyboardModeController
import com.github.shekohex.whisperpp.ui.keyboard.KeyboardScreen
import com.github.shekohex.whisperpp.ui.theme.WhisperToInputTheme

/** Builds the Compose input view and binds [KeyboardScreen] to the keyboard's state holders and actions. */
class KeyboardViewFactory(private val deps: Deps) {
    class Deps(
        val ui: KeyboardUiState,
        val language: KeyboardLanguageMenu,
        val undo: KeyboardUndoState,
        val notices: EnhancementNoticeState,
        val sendBlock: ExternalSendBlock,
        val disclosures: FirstUseDisclosureGate,
        val modes: KeyboardModeController,
        val session: DictationSession,
        val router: DictationIntentRouter,
        val commands: CommandModeController,
        val commandState: CommandModeState,
        val onDeleteText: () -> Unit,
        val onOpenSettings: (destination: String?) -> Unit,
        val onAllowMicPermission: () -> Unit,
        val onBlockedAction: () -> Unit,
    )

    /** [owners] are attached to the view and to [decorView], where dialogs and sheets look them up. */
    fun create(context: Context, owners: ImeViewLifecycle, decorView: View?): ComposeView {
        return ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // Decor view too: dialogs/popups/sheets look the owners up from the window root.
            owners.attach(this, decorView)

            setContent {
                WhisperToInputTheme {
                    KeyboardContent()
                }
            }
        }
    }

    @Composable
    private fun KeyboardContent() {
        val ui = deps.ui
        val language = deps.language
        val undo = deps.undo
        val sendBlock = deps.sendBlock
        val command = deps.commandState
        KeyboardScreen(
            state = ui.keyboardState.value,
            languageLabel = language.label.value,
            level = { ui.micLevel.floatValue },
            recordingTimeMs = { ui.recordingTimeMs.longValue },
            showLongPressHint = ui.showLongPressHint.value,
            tapToRecord = ui.tapToRecord.value,
            commandModeEnabled = ui.commandModeEnabled.value,
            showModeOnKeyboard = ui.showModeOnKeyboard.value,
            isStarting = ui.sessionStarting.value,
            undoAvailable = undo.undoAvailable.value,
            undoQuickActionVisible = undo.quickActionVisible.value,
            enhancementNotice = deps.notices.notice.value,
            enhancementUndoAvailable = undo.enhancementUndoAvailable.value,
            onRecordIntent = deps.router::onRecordIntent,
            onCancelAction = { deps.router.onCancelAction() },
            onDeleteAction = { deps.onDeleteText() },
            onUndoAction = { deps.router.onUndoAction() },
            onEnhancementUndoAction = { deps.router.onEnhancementUndoAction() },
            onDismissEnhancementNotice = { deps.notices.dismiss() },
            onOpenSettings = { deps.onOpenSettings(null) },
            onOpenSettingsDestination = { deps.onOpenSettings(it) },
            languageOptions = language.options.value,
            selectedLanguage = language.selected.value,
            onLanguageSelected = { language.select(it) },
            cancelConfirmationVisible = ui.pendingCancelConfirm.value != null,
            onCancelConfirmed = { deps.router.onCancelConfirmed() },
            onCancelConfirmDismissed = { ui.pendingCancelConfirm.value = null },
            onOverlayBackChanged = { ui.overlayBack = it },
            modeState = deps.modes.uiState.collectAsState().value,
            onModeSelected = { deps.modes.select(it) },
            onDismissHint = { ui.showLongPressHint.value = false },
            externalSendBlockedReason = sendBlock.reason.value,
            externalSendBlockedByAppPolicy = sendBlock.blockedByAppPolicy.value,
            blockedPackageName = sendBlock.blockedPackageName.value,
            showSecureFieldExplanation = !sendBlock.explanationDontShowAgain.value,
            firstUseDisclosure = deps.disclosures.uiState.value,
            onBlockedAction = { deps.onBlockedAction() },
            onDontShowSecureFieldExplanationAgain = { sendBlock.saveExplanationDontShowAgain() },
            onFirstUseDisclosureContinue = { deps.disclosures.onContinue() },
            onFirstUseDisclosureCancel = { deps.disclosures.cancel() },
            onFirstUseDisclosureOpenPrivacySafety = {
                deps.onOpenSettings(PRIVACY_SAFETY_DESTINATION)
                deps.disclosures.onOpenSettings()
            },
            micPermissionMissing = ui.micPermissionMissing.value,
            onAllowMicPermission = { deps.onAllowMicPermission() },
            pendingCommitText = ui.pendingCommitText.value,
            onPendingCommitInsert = { deps.session.onPendingCommitInsert() },
            onPendingCommitCopy = { deps.router.onPendingCommitCopy() },
            onPendingCommitDiscard = { deps.session.onPendingCommitDiscard() },

            commandModeActive = command.active,
            commandStage = command.stage,
            commandErrorMessage = command.errorMessage,
            commandUndoAvailable = command.undoAvailable,
            commandUndoRemainingMs = command.undoRemainingMs,
            commandSelectedPresetId = command.selectedPresetId,
            commandClipboardPreview = command.clipboardPreview,
            commandClipboardCharCount = command.clipboardCharCount,
            commandClipboardIsLarge = command.clipboardIsLarge,
            commandClipboardUnavailableReason = command.clipboardUnavailableReason,
            commandClipboardAttemptsRemaining = command.clipboardAttemptsRemaining,
            onCommandEnter = { deps.commands.onEnter() },
            onCommandCancel = { deps.commands.onCancel() },
            onCommandClipboardContinue = { deps.commands.onClipboardContinue() },
            onCommandClipboardRetry = { deps.commands.onClipboardRetry() },
            onCommandStopListening = { deps.commands.onStopListening() },
            onCommandRetry = { deps.commands.onRetryTransform() },
            onCommandUndo = { deps.commands.onUndo() },
            onCommandPresetSelected = { deps.commands.onPresetSelected(it) },
        )
    }

    private companion object {
        const val PRIVACY_SAFETY_DESTINATION = "privacy_safety"
    }
}
