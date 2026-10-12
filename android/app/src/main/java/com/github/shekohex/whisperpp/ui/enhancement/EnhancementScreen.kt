package com.github.shekohex.whisperpp.ui.enhancement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_BACKEND
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_MODEL
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_PROMPT
import com.github.shekohex.whisperpp.data.PreferenceKeys.USE_CONTEXT
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.DetailTopBar
import com.github.shekohex.whisperpp.ui.settings.common.SettingsDropdown
import com.github.shekohex.whisperpp.ui.settings.common.SettingsEditDialog
import com.github.shekohex.whisperpp.ui.settings.common.SettingsGroup
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.SettingsItem
import com.github.shekohex.whisperpp.ui.settings.common.SettingsToggle
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel

/** Post-dictation cleanup (old "Post processing" screen) plus links to prompts and presets. */
@Composable
fun EnhancementScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
    viewModel: EnhancementViewModel = containerViewModel { container, _ -> EnhancementViewModel(container) },
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()

    val selectedProviderId = prefs?.get(ENHANCEMENT_BACKEND) ?: ""
    val selectedModelId = prefs?.get(ENHANCEMENT_MODEL) ?: ""
    val selectedProvider = providers.find { it.id == selectedProviderId } ?: providers.firstOrNull()
    var showPromptDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { DetailTopBar(title = "Enhancement", onBack = onBack, helpTopic = SettingsHelpTopic.ENHANCEMENT) },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Changes apply immediately. Use a custom prompt only when the default cleanup behavior is not enough.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsGroup(title = "Behavior") {
                SettingsToggle(
                    icon = Icons.Default.AutoFixHigh,
                    title = "Enable enhancement",
                    checked = prefs?.get(ENHANCEMENT_ENABLED) ?: false,
                    onCheckedChange = { viewModel.set(ENHANCEMENT_ENABLED, it) }
                )
                SettingsToggle(
                    icon = Icons.Default.History,
                    title = "Use Context",
                    checked = prefs?.get(USE_CONTEXT) ?: false,
                    onCheckedChange = { viewModel.set(USE_CONTEXT, it) }
                )
                SettingsItem(
                    icon = Icons.Default.EditNote,
                    title = "Custom Prompt",
                    subtitle = (prefs?.get(ENHANCEMENT_PROMPT) ?: "Default").take(20) + "...",
                    onClick = { showPromptDialog = true }
                )
            }

            SettingsGroup(title = "LLM Configuration") {
                val itemModifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                SettingsDropdown(
                    label = "Backend Provider",
                    value = selectedProvider?.name ?: "Select Provider",
                    options = providers,
                    optionLabel = { it.name },
                    onSelect = viewModel::selectProvider,
                    modifier = itemModifier,
                    emptyLabel = "No Providers Configured",
                )
                val currentModelName = selectedProvider?.models?.find { it.id == selectedModelId }?.name ?: selectedModelId
                SettingsDropdown(
                    label = "Model",
                    value = currentModelName.ifEmpty { "Select Model" },
                    options = selectedProvider?.models.orEmpty(),
                    optionLabel = { it.name },
                    onSelect = { viewModel.set(ENHANCEMENT_MODEL, it.id) },
                    modifier = itemModifier,
                    enabled = selectedProvider != null,
                    emptyLabel = "No Models",
                )
            }

            SettingsGroup(title = "Prompts") {
                SettingsItem(
                    icon = Icons.Default.TextFields,
                    title = stringResource(R.string.enhancement_prompts_link_title),
                    subtitle = stringResource(R.string.enhancement_prompts_link_summary),
                    onClick = { onNavigate(Destination.Prompts) },
                )
                SettingsItem(
                    icon = Icons.Default.AutoFixHigh,
                    title = stringResource(R.string.settings_transform_presets_title),
                    subtitle = stringResource(R.string.enhancement_presets_link_summary),
                    onClick = { onNavigate(Destination.Presets) },
                )
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (showPromptDialog) {
        SettingsEditDialog(
            title = "Custom System Prompt",
            initialValue = prefs?.get(ENHANCEMENT_PROMPT) ?: "",
            onDismiss = { showPromptDialog = false },
            onConfirm = { newValue ->
                viewModel.set(ENHANCEMENT_PROMPT, newValue)
                showPromptDialog = false
            }
        )
    }
}
