package com.github.shekohex.whisperpp.ui.enhancement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESETS
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESET_ID_CLEANUP
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESET_ID_TONE_REWRITE
import com.github.shekohex.whisperpp.data.presetById
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.components.SectionCard
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel

@Composable
fun PresetsScreen(
    onBack: () -> Unit,
    viewModel: PresetsViewModel = containerViewModel { container, _ -> PresetsViewModel(container) },
) {
    val enhancementPresetId by viewModel.enhancementPresetId.collectAsStateWithLifecycle()
    val commandPresetId by viewModel.commandPresetId.collectAsStateWithLifecycle()
    val enhancementPreset = presetById(enhancementPresetId) ?: presetById(TRANSFORM_PRESET_ID_CLEANUP)
    val commandPreset = presetById(commandPresetId) ?: presetById(TRANSFORM_PRESET_ID_TONE_REWRITE)

    var showEnhancementPicker by rememberSaveable { mutableStateOf(false) }
    var showCommandPicker by rememberSaveable { mutableStateOf(false) }

    PageScaffold(
        title = stringResource(R.string.settings_transform_presets_title),
        onBack = onBack,
        actions = { SettingsHelpAction(SettingsHelpTopic.PRESETS) },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.presets_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionCard(title = stringResource(R.string.settings_transform_presets_group_title)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_transform_presets_enhancement_label)) },
                        supportingContent = {
                            val title = enhancementPreset?.let { stringResource(it.titleRes) }
                                ?: stringResource(R.string.transform_preset_cleanup_title)
                            Text(stringResource(R.string.presets_summary, title, stringResource(R.string.settings_transform_presets_enhancement_helper)))
                        },
                        leadingContent = { Icon(Icons.Default.AutoFixHigh, null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        modifier = Modifier.clickable(role = Role.Button) { showEnhancementPicker = true },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_transform_presets_command_label)) },
                        supportingContent = {
                            val title = commandPreset?.let { stringResource(it.titleRes) }
                                ?: stringResource(R.string.transform_preset_tone_rewrite_title)
                            Text(stringResource(R.string.presets_summary, title, stringResource(R.string.settings_transform_presets_command_helper)))
                        },
                        leadingContent = { Icon(Icons.Default.EditNote, null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        modifier = Modifier.clickable(role = Role.Button) { showCommandPicker = true },
                    )
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (showEnhancementPicker) {
        PresetPickerDialog(
            selectedId = enhancementPresetId,
            onSelect = {
                showEnhancementPicker = false
                viewModel.setEnhancementPreset(it)
            },
            onDismiss = { showEnhancementPicker = false },
        )
    }
    if (showCommandPicker) {
        PresetPickerDialog(
            selectedId = commandPresetId,
            onSelect = {
                showCommandPicker = false
                viewModel.setCommandPreset(it)
            },
            onDismiss = { showCommandPicker = false },
        )
    }
}

@Composable
private fun PresetPickerDialog(
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_transform_presets_picker_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                TRANSFORM_PRESETS.forEachIndexed { index, preset ->
                    ListItem(
                        headlineContent = { Text(stringResource(preset.titleRes)) },
                        supportingContent = { Text(stringResource(preset.descriptionRes)) },
                        leadingContent = { RadioButton(selected = selectedId == preset.id, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = selectedId == preset.id,
                            role = Role.RadioButton,
                            onClick = { onSelect(preset.id) },
                        ),
                    )
                    if (index < TRANSFORM_PRESETS.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close))
            }
        },
    )
}
