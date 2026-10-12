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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.PreferenceKeys.SPEECH_TO_TEXT_BACKEND
import com.github.shekohex.whisperpp.data.Presets
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.ui.components.EmptyState
import com.github.shekohex.whisperpp.ui.components.LoadingState
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.WidthPreviews
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
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

    var blockedDelete by remember { mutableStateOf<Pair<ServiceProvider, List<Int>>?>(null) }
    var showAddOptions by remember { mutableStateOf(false) }

    PageScaffold(
        title = stringResource(R.string.provider_list_title),
        onBack = onBack,
        actions = {
            SettingsHelpAction(SettingsHelpTopic.PROVIDERS)
            IconButton(onClick = { onNavigate(Destination.ProviderSelections) }) {
                Icon(Icons.Default.SettingsSuggest, stringResource(R.string.provider_list_selections_action))
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddOptions = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.provider_list_add))
            }
        },
    ) {
        ProviderListContent(
            providers = providers,
            activeProviderId = prefs?.get(SPEECH_TO_TEXT_BACKEND),
            onAdd = { showAddOptions = true },
            onSelectActive = viewModel::selectActive,
            onOpen = { onNavigate(Destination.ProviderEdit(id = it.id)) },
            onDelete = { provider ->
                val refs = viewModel.delete(provider)
                if (refs.isNotEmpty()) blockedDelete = provider to refs
            },
        )
    }

    blockedDelete?.let { (provider, refs) ->
        BlockedDeleteDialog(
            title = stringResource(R.string.provider_delete_blocked_title),
            message = stringResource(R.string.provider_delete_blocked_message, provider.name, selectionNames(refs)),
            onDismiss = { blockedDelete = null },
            onGoToSelections = {
                blockedDelete = null
                onNavigate(Destination.ProviderSelections)
            },
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

/** Stateless list body; `null` [providers] means still loading. */
@Composable
private fun ProviderListContent(
    providers: List<ServiceProvider>?,
    activeProviderId: String?,
    onAdd: () -> Unit,
    onSelectActive: (ServiceProvider) -> Unit,
    onOpen: (ServiceProvider) -> Unit,
    onDelete: (ServiceProvider) -> Unit,
) {
    when {
        providers == null -> LoadingState()
        providers.isEmpty() -> EmptyState(
            icon = Icons.Default.CloudOff,
            title = stringResource(R.string.provider_list_empty_title),
            body = stringResource(R.string.provider_list_empty_message),
            modifier = Modifier.fillMaxSize(),
            action = {
                Button(onClick = onAdd) {
                    Text(stringResource(R.string.provider_list_add))
                }
            },
        )
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = 80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.provider_list_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(providers, key = { it.id }) { provider ->
                ProviderCard(
                    provider = provider,
                    isActive = provider.id == activeProviderId,
                    onSelectActive = { onSelectActive(provider) },
                    onClick = { onOpen(provider) },
                    onDelete = { onDelete(provider) },
                )
            }
        }
    }
}

@Composable
internal fun BlockedDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onGoToSelections: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onGoToSelections) {
                Text(stringResource(R.string.provider_go_to_selections))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.component_cancel))
            }
        },
    )
}

@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onCustom: () -> Unit,
    onPreset: (ServiceProvider) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.provider_list_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AddOptionRow(
                    title = stringResource(R.string.provider_add_custom),
                    supporting = null,
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = onCustom,
                )
                HorizontalDivider()
                Text(
                    stringResource(R.string.provider_add_presets),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() },
                )
                Presets.AllPresets.forEach { preset ->
                    AddOptionRow(
                        title = preset.name,
                        supporting = stringResource(preset.type.labelRes()),
                        leadingIcon = { Icon(Icons.Default.Bookmarks, null) },
                        onClick = { onPreset(preset) },
                    )
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

@Composable
private fun AddOptionRow(
    title: String,
    supporting: String?,
    leadingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { text -> { Text(text) } },
        leadingContent = leadingIcon,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
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
    val activeLabel = stringResource(R.string.provider_list_active)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = isActive,
                    onClick = onSelectActive,
                    modifier = Modifier.semantics { contentDescription = activeLabel },
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Dns, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        provider.name,
                        style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(provider.type.labelRes()), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        stringResource(R.string.provider_delete_named, provider.name),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
            if (provider.models.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    pluralStringResource(R.plurals.provider_models_configured, provider.models.size, provider.models.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@WidthPreviews
@Composable
private fun ProviderListContentPreview() {
    PreviewSurface {
        ProviderListContent(
            providers = listOf(
                ServiceProvider(
                    id = "openai",
                    name = "OpenAI",
                    type = ProviderType.OPENAI,
                    endpoint = "https://api.openai.com/v1",
                    models = listOf(ModelConfig("whisper-1", "whisper-1"), ModelConfig("gpt-4o", "GPT-4o")),
                ),
                ServiceProvider(
                    id = "local",
                    name = "Local Whisper server with a long name",
                    type = ProviderType.WHISPER_ASR,
                    endpoint = "http://192.168.1.10:9000/asr",
                    models = emptyList(),
                ),
            ),
            activeProviderId = "openai",
            onAdd = {},
            onSelectActive = {},
            onOpen = {},
            onDelete = {},
        )
    }
}
