package com.github.shekohex.whisperpp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.permissions.KeyboardSetup
import com.github.shekohex.whisperpp.domain.setup.SetupStatus
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.SectionCard
import com.github.shekohex.whisperpp.ui.components.SetupStepChip
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.components.rememberMicPermissionAction
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.theme.Spacing

const val SETUP_CHECKLIST_TAG = "home_setup_checklist"

/** Home's setup checklist (§4.9 item 3): wires each row to its system screen or permission request. */
@Composable
internal fun SetupChecklistCard(
    status: SetupStatus,
    hasProviders: Boolean,
    onNavigate: (Destination) -> Unit,
    onMicRequested: () -> Unit,
    onMicResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status.isComplete) return
    val context = LocalContext.current
    val micAction = rememberMicPermissionAction(
        granted = status.micGranted,
        requestedBefore = status.micRequestedBefore,
        onRequested = onMicRequested,
        onResult = { onMicResult() },
    )
    SetupChecklist(
        status = status,
        micPermanentlyDenied = micAction.permanentlyDenied,
        hasProviders = hasProviders,
        onEnableKeyboard = { KeyboardSetup.openInputMethodSettings(context) },
        onSelectKeyboard = { KeyboardSetup.showInputMethodPicker(context) },
        onMic = micAction.onClick,
        onProvider = {
            onNavigate(if (hasProviders) Destination.ProviderSelections else Destination.ProviderEdit())
        },
        modifier = modifier,
    )
}

/** Stateless checklist; renders nothing once every required step is green. */
@Composable
fun SetupChecklist(
    status: SetupStatus,
    micPermanentlyDenied: Boolean,
    hasProviders: Boolean,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    onMic: () -> Unit,
    onProvider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status.isComplete) return
    SectionCard(
        title = stringResource(R.string.setup_checklist_title),
        modifier = modifier.testTag(SETUP_CHECKLIST_TAG),
    ) {
        Text(
            text = stringResource(R.string.setup_checklist_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        )
        ChecklistRow(
            icon = Icons.Filled.Keyboard,
            title = stringResource(R.string.setup_checklist_enable),
            done = status.imeEnabled,
            doneText = stringResource(R.string.setup_status_keyboard_enabled),
            pendingText = stringResource(R.string.setup_status_keyboard_not_enabled),
            actionLabel = stringResource(R.string.setup_action_enable),
            onAction = onEnableKeyboard,
        )
        ChecklistRow(
            icon = Icons.Filled.TouchApp,
            title = stringResource(R.string.setup_checklist_select),
            done = status.imeSelected,
            doneText = stringResource(R.string.setup_status_keyboard_selected),
            pendingText = stringResource(R.string.setup_status_keyboard_not_selected),
            actionLabel = stringResource(R.string.setup_action_select),
            onAction = onSelectKeyboard,
            actionEnabled = status.imeEnabled,
        )
        ChecklistRow(
            icon = Icons.Filled.Mic,
            title = stringResource(R.string.setup_checklist_mic),
            done = status.micGranted,
            doneText = stringResource(R.string.setup_status_mic_granted),
            pendingText = stringResource(R.string.setup_status_mic_not_granted),
            actionLabel = stringResource(
                if (micPermanentlyDenied) R.string.setup_action_open_settings else R.string.setup_action_allow,
            ),
            onAction = onMic,
        )
        ChecklistRow(
            icon = Icons.Filled.Cloud,
            title = stringResource(R.string.setup_checklist_provider),
            done = status.sttProviderConfigured,
            doneText = stringResource(R.string.setup_status_provider_ready),
            pendingText = stringResource(R.string.setup_status_provider_missing),
            actionLabel = stringResource(if (hasProviders) R.string.setup_action_choose else R.string.setup_action_add),
            onAction = onProvider,
        )
    }
}

@Composable
private fun ChecklistRow(
    icon: ImageVector,
    title: String,
    done: Boolean,
    doneText: String,
    pendingText: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            SetupStepChip(done = done, doneText = doneText, pendingText = pendingText)
        }
        if (!done) {
            TextButton(onClick = onAction, enabled = actionEnabled) { Text(actionLabel) }
        }
    }
}

@ThemePreviews
@Composable
private fun SetupChecklistPreview() {
    PreviewSurface {
        SetupChecklist(
            status = SetupStatus(imeEnabled = true, micGranted = false),
            micPermanentlyDenied = false,
            hasProviders = false,
            onEnableKeyboard = {},
            onSelectKeyboard = {},
            onMic = {},
            onProvider = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
