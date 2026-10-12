package com.github.shekohex.whisperpp.ui.backup

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.data.RestoreMode

// TODO(WP-3.6): move the restore wizard steps into BackupRestoreViewModel with a sealed RestoreStage.

/** Password + confirmation for a new encrypted export; [onConfirm] receives the password. */
@Composable
internal fun ExportPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Protect backup with a password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Every export is encrypted. Provider credentials can be included, so there is no plain export path.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasswordField(password, "Backup password", visible) {
                    password = it
                    error = null
                }
                PasswordField(confirmation, "Confirm password", visible) {
                    confirmation = it
                    error = null
                }
                error?.let { ErrorText(it) }
                TextButton(onClick = { visible = !visible }) {
                    Text(if (visible) "Hide password" else "Show password")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        password.isBlank() -> error = "Enter a password before creating the backup."
                        password != confirmation -> error = "Passwords do not match."
                        else -> onConfirm(password)
                    }
                },
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/** Asks for the backup password before previewing; [error] comes from a failed decrypt. */
@Composable
internal fun ImportPasswordDialog(
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onErrorCleared: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlock backup preview") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter the password before previewing or applying anything from this backup.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasswordField(password, "Backup password", visible) {
                    password = it
                    onErrorCleared()
                }
                error?.let { ErrorText(it) }
                TextButton(onClick = { visible = !visible }) {
                    Text(if (visible) "Hide password" else "Show password")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }) {
                Text("Preview restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
                text = "Choose how to restore this backup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Overwrite restores every category found in the file. Merge lets you preview and include or exclude whole categories before applying anything.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RestoreModeOption(
                title = "Overwrite",
                summary = "Replace every category present in the backup file.",
                onClick = { onSelect(RestoreMode.OVERWRITE) },
            )
            RestoreModeOption(
                title = "Merge",
                summary = "Preview the backup first, then choose which categories to include. Imported values win conflicts.",
                onClick = { onSelect(RestoreMode.MERGE) },
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
            .clickable(onClick = onClick),
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
