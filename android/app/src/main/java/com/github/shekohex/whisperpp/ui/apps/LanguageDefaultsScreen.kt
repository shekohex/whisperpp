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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.data.LanguageProfile
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.ui.settings.common.DetailTopBar
import com.github.shekohex.whisperpp.ui.settings.common.SettingsGroup
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import java.util.Locale

@Composable
fun LanguageDefaultsScreen(
    onBack: () -> Unit,
    viewModel: LanguageDefaultsViewModel = containerViewModel { container, _ -> LanguageDefaultsViewModel(container) },
) {
    val context = LocalContext.current
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val languageDefaults by viewModel.languageDefaults.collectAsStateWithLifecycle()

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var expandedCode by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            DetailTopBar(
                title = "Per-language defaults",
                onBack = onBack,
                helpTopic = SettingsHelpTopic.LANGUAGE_DEFAULTS,
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add")
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
                text = "Add a language when one global setup is not enough. Changes save as soon as you choose them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (languageDefaults.isEmpty()) {
                SettingsGroup(title = "Languages") {
                    Text(
                        text = "No per-language defaults yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            } else {
                val sorted = remember(languageDefaults) { languageDefaults.sortedBy { it.languageCode.lowercase(Locale.getDefault()) } }
                sorted.forEach { profile ->
                    val isExpanded = expandedCode.equals(profile.languageCode, ignoreCase = true)
                    LanguageDefaultCard(
                        profile = profile,
                        providers = providers,
                        expanded = isExpanded,
                        onToggleExpanded = { expandedCode = if (isExpanded) null else profile.languageCode },
                        onDelete = {
                            viewModel.delete(profile.languageCode) {
                                Toast.makeText(context, "Removed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUpdate = viewModel::upsert,
                    )
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (showAddDialog) {
        AddLanguageDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { code ->
                viewModel.add(code) { added ->
                    Toast.makeText(context, if (added) "Added" else "Already exists", Toast.LENGTH_SHORT).show()
                    expandedCode = code.trim()
                    showAddDialog = false
                }
            },
        )
    }
}

@Composable
private fun LanguageDefaultCard(
    profile: LanguageProfile,
    providers: List<ServiceProvider>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (LanguageProfile) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = profile.languageCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "STT + Text defaults",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleExpanded) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Expand")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete")
                }
            }
            if (expanded) {
                ProviderModelSelectionEditor(
                    title = "Dictation (STT)",
                    providers = providers,
                    isCompatible = { it.capabilities.stt },
                    providerId = profile.transcriptionProviderId,
                    modelId = profile.transcriptionModelId,
                    onUpdate = { p, m -> onUpdate(profile.copy(transcriptionProviderId = p, transcriptionModelId = m)) },
                )
                ProviderModelSelectionEditor(
                    title = "Enhancement (Text)",
                    providers = providers,
                    isCompatible = { it.capabilities.text },
                    providerId = profile.enhancementProviderId,
                    modelId = profile.enhancementModelId,
                    onUpdate = { p, m -> onUpdate(profile.copy(enhancementProviderId = p, enhancementModelId = m)) },
                )
            }
        }
    }
}

@Composable
private fun AddLanguageDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var draftLanguageCode by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add language") },
        text = {
            OutlinedTextField(
                value = draftLanguageCode,
                onValueChange = { draftLanguageCode = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Language code") },
                placeholder = { Text("e.g. en, en-US, ar") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(draftLanguageCode) }, enabled = draftLanguageCode.trim().isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
