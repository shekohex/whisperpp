package com.github.shekohex.whisperpp.ui.providers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.PreferenceKeys.SPEECH_TO_TEXT_BACKEND
import com.github.shekohex.whisperpp.data.Presets
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.ui.components.EmptyState
import com.github.shekohex.whisperpp.ui.components.LoadingState
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.DetailTopBar
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel

/** "AI Models": the configured providers, with add/delete and the active backend radio. */
@Composable
fun ProviderListScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
    viewModel: ProviderListViewModel = containerViewModel { container, _ -> ProviderListViewModel(container) },
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val activeBackendId = prefs?.get(SPEECH_TO_TEXT_BACKEND)

    var blockedDelete by remember { mutableStateOf<Pair<ServiceProvider, List<String>>?>(null) }
    var showAddOptions by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DetailTopBar(
                title = "Providers",
                onBack = onBack,
                helpTopic = SettingsHelpTopic.PROVIDERS,
                actions = {
                    IconButton(onClick = { onNavigate(Destination.ProviderSelections) }) {
                        Icon(Icons.Default.SettingsSuggest, stringResource(R.string.provider_list_selections_action))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddOptions = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.provider_list_add))
            }
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        val loaded = providers
        when {
            loaded == null -> LoadingState(Modifier.padding(padding))
            loaded.isEmpty() -> EmptyState(
                icon = Icons.Default.CloudOff,
                title = stringResource(R.string.provider_list_empty_title),
                body = stringResource(R.string.provider_list_empty_message),
                modifier = Modifier.fillMaxSize().padding(padding),
                action = {
                    Button(onClick = { showAddOptions = true }) {
                        Text(stringResource(R.string.provider_list_add))
                    }
                },
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Add or test providers here, then open Provider selections to choose the active defaults Whisper++ should use.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(loaded) { provider ->
                    ProviderCard(
                        provider = provider,
                        isActive = provider.id == activeBackendId,
                        onSelectActive = { viewModel.selectActive(provider) },
                        onClick = { onNavigate(Destination.ProviderEdit(id = provider.id)) },
                        onDelete = {
                            val refs = viewModel.delete(provider)
                            if (refs.isNotEmpty()) blockedDelete = provider to refs
                        }
                    )
                }
            }
        }
    }

    blockedDelete?.let { (provider, refs) ->
        AlertDialog(
            onDismissRequest = { blockedDelete = null },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("Can't delete selected provider") },
            text = { Text("${provider.name} is selected for: ${refs.joinToString(", ")}. Reassign or clear the selection first.") },
            confirmButton = {
                TextButton(onClick = {
                    blockedDelete = null
                    onNavigate(Destination.ProviderSelections)
                }) {
                    Text("Go to selections")
                }
            },
            dismissButton = {
                TextButton(onClick = { blockedDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddOptions) {
        AddProviderDialog(
            onDismiss = { showAddOptions = false },
            onCustom = {
                showAddOptions = false
                onNavigate(Destination.ProviderEdit())
            },
            onPreset = { preset ->
                viewModel.addPreset(preset) { id ->
                    showAddOptions = false
                    onNavigate(Destination.ProviderEdit(id = id))
                }
            },
        )
    }
}

@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onCustom: () -> Unit,
    onPreset: (ServiceProvider) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListItem(
                    headlineContent = { Text("Custom Provider") },
                    leadingContent = { Icon(Icons.Default.Edit, null) },
                    modifier = Modifier.clickable(onClick = onCustom)
                )
                HorizontalDivider()
                Text("Presets", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Presets.AllPresets.forEach { preset ->
                    ListItem(
                        headlineContent = { Text(preset.name) },
                        supportingContent = { Text(preset.type.toString()) },
                        leadingContent = { Icon(Icons.Default.Bookmarks, null) },
                        modifier = Modifier.clickable { onPreset(preset) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ProviderCard(
    provider: ServiceProvider,
    isActive: Boolean,
    onSelectActive: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = isActive, onClick = onSelectActive)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Dns, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(provider.type.toString(), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
                Icon(Icons.Default.ChevronRight, null)
            }
            if (provider.models.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${provider.models.size} Models Configured",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
