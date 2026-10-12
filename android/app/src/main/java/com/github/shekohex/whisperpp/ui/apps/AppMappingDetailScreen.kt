package com.github.shekohex.whisperpp.ui.apps

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.data.AppendMode
import com.github.shekohex.whisperpp.data.ProviderModelOverride
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.ui.settings.common.DetailTopBar
import com.github.shekohex.whisperpp.ui.settings.common.SettingsDropdown
import com.github.shekohex.whisperpp.ui.settings.common.SettingsGroup
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import java.util.Locale

@Composable
fun AppMappingDetailScreen(
    packageName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = containerViewModel(key = "app-mapping-$packageName") { container, handle ->
        AppMappingDetailViewModel(container, handle, packageName)
    }
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val promptProfiles by viewModel.promptProfiles.collectAsStateWithLifecycle()
    val hasSavedMapping by viewModel.hasSavedMapping.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()

    val profileId = draft.profileId?.trim().orEmpty()
    val missingProfile = profileId.isNotBlank() && promptProfiles.none { it.id == profileId }

    Scaffold(
        topBar = {
            DetailTopBar(
                title = "App mapping",
                onBack = onBack,
                helpTopic = SettingsHelpTopic.APP_MAPPINGS,
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.save {
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        enabled = loaded,
                    ) {
                        Text("Save")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Save applies this mapping right away. Leave any field empty to inherit the global defaults.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsGroup(title = "App") {
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            SettingsGroup(title = "Profile") {
                ProfilePicker(
                    profiles = promptProfiles,
                    profileId = draft.profileId,
                    missingProfile = missingProfile,
                    onSelect = { id -> viewModel.update { it.copy(profileId = id) } },
                )
            }
            SettingsGroup(title = "Prompt append") {
                AppendEditor(
                    appendMode = draft.appendMode,
                    append = draft.appPromptAppend.orEmpty(),
                    onModeChange = { mode -> viewModel.update { it.copy(appendMode = mode) } },
                    onAppendChange = { text -> viewModel.update { it.copy(appPromptAppend = text) } },
                )
            }
            ProviderModelSelectionEditor(
                title = "Dictation override (STT)",
                providers = providers,
                isCompatible = { it.capabilities.stt },
                providerId = draft.sttOverride?.providerId.orEmpty(),
                modelId = draft.sttOverride?.modelId.orEmpty(),
                onUpdate = { p, m -> viewModel.update { it.copy(sttOverride = ProviderModelOverride(p, m)) } },
            )
            ProviderModelSelectionEditor(
                title = "Enhancement override (Text)",
                providers = providers,
                isCompatible = { it.capabilities.text },
                providerId = draft.textOverride?.providerId.orEmpty(),
                modelId = draft.textOverride?.modelId.orEmpty(),
                onUpdate = { p, m -> viewModel.update { it.copy(textOverride = ProviderModelOverride(p, m)) } },
            )
            if (hasSavedMapping) {
                OutlinedButton(
                    onClick = {
                        viewModel.remove {
                            Toast.makeText(context, "Removed mapping", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Remove mapping")
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun ProfilePicker(
    profiles: List<PromptProfile>,
    profileId: String?,
    missingProfile: Boolean,
    onSelect: (String?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val selected = profiles.firstOrNull { it.id == profileId }
        SettingsDropdown(
            label = "Prompt profile",
            value = selected?.name ?: profileId?.takeIf { it.isNotBlank() } ?: "None",
            options = profiles.sortedBy { it.name.lowercase(Locale.getDefault()) },
            optionLabel = { it.name },
            onSelect = { onSelect(it.id) },
            clearLabel = "None",
            onClear = { onSelect(null) },
        )
        if (missingProfile) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        text = "Missing profile; mapping will fallback until updated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppendEditor(
    appendMode: AppendMode,
    append: String,
    onModeChange: (AppendMode) -> Unit,
    onAppendChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = appendMode == AppendMode.APPEND,
                onClick = { onModeChange(AppendMode.APPEND) },
                label = { Text("Append") },
            )
            FilterChip(
                selected = appendMode == AppendMode.NO_APPEND,
                onClick = { onModeChange(AppendMode.NO_APPEND) },
                label = { Text("No append") },
            )
        }
        if (appendMode == AppendMode.NO_APPEND) {
            Text(
                text = "Append disabled for this app. Base prompt still applies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            OutlinedTextField(
                value = append,
                onValueChange = onAppendChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 10,
                label = { Text("App-specific append (optional)") },
            )
        }
    }
}
