package com.github.shekohex.whisperpp.ui.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R

@Composable
internal fun TestResultDialog(result: ProviderTestResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(result.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                result.status?.let {
                    Text(stringResource(R.string.provider_test_http_status, it), style = MaterialTheme.typography.labelMedium)
                }
                result.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (result.body.isNotBlank()) {
                    Text(
                        result.body,
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                    )
                } else if (result.error == null) {
                    Text(
                        stringResource(R.string.provider_test_empty_response),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close))
            }
        }
    )
}

@Composable
internal fun BlockedModelDeleteDialog(
    modelName: String,
    refs: List<Int>,
    onDismiss: () -> Unit,
    onGoToSelections: () -> Unit,
) {
    BlockedDeleteDialog(
        title = stringResource(R.string.provider_model_delete_blocked_title),
        message = stringResource(R.string.provider_model_delete_blocked_message, modelName, selectionNames(refs)),
        onDismiss = onDismiss,
        onGoToSelections = onGoToSelections,
    )
}
