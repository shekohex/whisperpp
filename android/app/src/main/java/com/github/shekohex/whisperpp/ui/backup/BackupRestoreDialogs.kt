package com.github.shekohex.whisperpp.ui.backup

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.RestoreMode

// TODO(WP-3.6): move the restore wizard steps into BackupRestoreViewModel with a sealed RestoreStage.

/** Password + confirmation for a new encrypted export; [onConfirm] receives the password. */
@Composable
internal fun ExportPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_export_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.backup_export_password_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasswordField(password, stringResource(R.string.backup_password_label), visible) {
                    password = it
                    error = null
                }
                PasswordField(confirmation, stringResource(R.string.backup_password_confirm_label), visible) {
                    confirmation = it
                    error = null
                }
                error?.let { ErrorText(stringResource(it)) }
                TextButton(onClick = { visible = !visible }) {
                    Text(stringResource(if (visible) R.string.backup_hide_password else R.string.backup_show_password))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        password.isBlank() -> error = R.string.backup_error_password_blank
                        password != confirmation -> error = R.string.backup_error_password_mismatch
                        else -> onConfirm(password)
                    }
                },
            ) {
                Text(stringResource(R.string.backup_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.component_cancel))
            }
        },
    )
}

/** Asks for the backup password before previewing; [error] comes from a failed decrypt. */
@Composable
internal fun ImportPasswordDialog(
    @StringRes error: Int?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onErrorCleared: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.backup_import_password_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasswordField(password, stringResource(R.string.backup_password_label), visible) {
                    password = it
                    onErrorCleared()
                }
                error?.let { ErrorText(stringResource(it)) }
                TextButton(onClick = { visible = !visible }) {
                    Text(stringResource(if (visible) R.string.backup_hide_password else R.string.backup_show_password))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }) {
                Text(stringResource(R.string.backup_preview_restore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.component_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RestoreModeSheet(onDismiss: () -> Unit, onSelect: (RestoreMode) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.backup_mode_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.backup_mode_sheet_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RestoreModeOption(
                title = stringResource(R.string.backup_mode_overwrite),
                summary = stringResource(R.string.backup_mode_overwrite_summary),
                onClick = { onSelect(RestoreMode.OVERWRITE) },
            )
            RestoreModeOption(
                title = stringResource(R.string.backup_mode_merge),
                summary = stringResource(R.string.backup_mode_merge_summary),
                onClick = { onSelect(RestoreMode.MERGE) },
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

// TODO(WP-3.7): adopt shared components (SecureTextField).
@Composable
private fun PasswordField(
    value: String,
    label: String,
    visible: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
    )
}

@Composable
private fun RestoreModeOption(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
