package com.github.shekohex.whisperpp.ui.keyboard

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
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
