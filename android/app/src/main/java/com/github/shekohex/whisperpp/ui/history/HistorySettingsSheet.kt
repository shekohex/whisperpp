package com.github.shekohex.whisperpp.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.HistorySettings
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.SettingsSwitchRow
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** Retention windows offered in the sheet; 0 = forever. A restored custom value is added as well. */
internal fun retentionOptions(currentDays: Int): List<Int> {
    val windows = buildSet {
        addAll(listOf(7, 30, 90))
        if (currentDays > 0) add(currentDays)
    }
    return windows.sorted() + 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySettingsSheet(
    settings: HistorySettings,
    onEnabledChange: (Boolean) -> Unit,
    onKeepAudioChange: (Boolean) -> Unit,
    onRetentionChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        HistorySettingsContent(settings, onEnabledChange, onKeepAudioChange, onRetentionChange)
    }
}

@Composable
internal fun HistorySettingsContent(
    settings: HistorySettings,
    onEnabledChange: (Boolean) -> Unit,
    onKeepAudioChange: (Boolean) -> Unit,
    onRetentionChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.history_settings_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        SettingsSwitchRow(
            title = stringResource(R.string.history_settings_enabled),
            supporting = stringResource(R.string.history_settings_enabled_desc),
            checked = settings.enabled,
            onCheckedChange = onEnabledChange,
            leadingIcon = Icons.Default.History,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.history_settings_keep_audio),
            supporting = stringResource(R.string.history_settings_keep_audio_desc),
            checked = settings.keepAudio,
            onCheckedChange = onKeepAudioChange,
            leadingIcon = Icons.Default.Mic,
            enabled = settings.enabled,
        )
        Text(
            text = stringResource(R.string.history_settings_retention),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg),
        )
        Text(
            text = stringResource(R.string.history_settings_retention_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            retentionOptions(settings.retentionDays).forEach { days ->
                FilterChip(
                    selected = settings.retentionDays == days,
                    onClick = { onRetentionChange(days) },
                    label = {
                        Text(
                            if (days == 0) {
                                stringResource(R.string.history_retention_forever)
                            } else {
                                pluralStringResource(R.plurals.history_retention_days, days, days)
                            },
                        )
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HistorySettingsContentPreview() {
    PreviewSurface {
        HistorySettingsContent(HistorySettings(keepAudio = true), {}, {}, {})
    }
}
