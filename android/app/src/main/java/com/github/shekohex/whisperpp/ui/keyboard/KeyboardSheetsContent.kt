package com.github.shekohex.whisperpp.ui.keyboard

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.privacy.SecureFieldDetector

internal fun startBlockedExplanationActivity(
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

/** Full blocked-sending explanation with its own title row; used by [BlockedExplanationActivity]. */
@Composable
internal fun BlockedExplanationContent(
    copy: BlockedExplanationCopySpec,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    onDontShowAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(copy.titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.keyboard_sheet_close))
            }
        }
        BlockedExplanationBody(copy = copy, onOpenSettings = onOpenSettings, onDontShowAgain = onDontShowAgain)
        Spacer(Modifier.height(12.dp))
    }
}

/** Blocked-sending explanation as an in-keyboard sheet (H-14). */
@Composable
internal fun BlockedExplanationSheet(
    copy: BlockedExplanationCopySpec,
    maxHeight: Dp,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    onDontShowAgain: () -> Unit,
) {
    KeyboardInlineSheet(title = stringResource(copy.titleRes), maxHeight = maxHeight, onDismiss = onClose) {
        BlockedExplanationBody(copy = copy, onOpenSettings = onOpenSettings, onDontShowAgain = onDontShowAgain)
    }
}

@Composable
private fun ColumnScope.BlockedExplanationBody(
    copy: BlockedExplanationCopySpec,
    onOpenSettings: () -> Unit,
    onDontShowAgain: () -> Unit,
) {
    val reasonText = copy.reasonArg?.let { arg ->
        stringResource(copy.reasonRes, arg)
    } ?: stringResource(copy.reasonRes)
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
    Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.secure_field_sheet_open_settings))
    }
    if (copy.showDontShowAgain) {
        TextButton(onClick = onDontShowAgain, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.secure_field_sheet_dont_show_again))
        }
    }
}

/** "What is sent where" before the first cloud request of a mode, as an in-keyboard sheet (H-14). */
@Composable
internal fun FirstUseDisclosureSheet(
    disclosure: FirstUseDisclosureUiState,
    maxHeight: Dp,
    onContinue: () -> Unit,
    onOpenPrivacySafety: () -> Unit,
    onCancel: () -> Unit,
) {
    KeyboardInlineSheet(title = disclosure.title, maxHeight = maxHeight, onDismiss = onCancel) {
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
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
        Text(
            text = disclosure.contextLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.keyboard_disclosure_continue))
        }
        OutlinedButton(onClick = onOpenPrivacySafety, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.keyboard_disclosure_open_privacy))
        }
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.keyboard_disclosure_cancel))
        }
    }
}
