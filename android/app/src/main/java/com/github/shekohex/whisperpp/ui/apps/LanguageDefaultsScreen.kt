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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.LanguageProfile
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.settings.common.SettingsGroup
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
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

    PageScaffold(
        title = stringResource(R.string.modes_language_defaults_title),
        onBack = onBack,
        actions = {
            SettingsHelpAction(SettingsHelpTopic.LANGUAGE_DEFAULTS)
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.apps_language_add))
            }
        },
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
                text = stringResource(R.string.apps_language_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (languageDefaults.isEmpty()) {
                SettingsGroup(title = stringResource(R.string.apps_languages_group)) {
                    Text(
                        text = stringResource(R.string.apps_language_empty),
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
                                Toast.makeText(context, R.string.apps_language_removed, Toast.LENGTH_SHORT).show()
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
                    val message = if (added) R.string.apps_language_added else R.string.apps_language_exists
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
                        text = stringResource(R.string.apps_language_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.apps_language_collapse else R.string.apps_language_expand,
                        ),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, stringResource(R.string.apps_language_delete, profile.languageCode))
                }
            }
            if (expanded) {
                ProviderModelSelectionEditor(
                    title = stringResource(R.string.provider_selection_ref_dictation),
                    providers = providers,
                    isCompatible = { it.capabilities.stt },
                    providerId = profile.transcriptionProviderId,
                    modelId = profile.transcriptionModelId,
                    onUpdate = { p, m -> onUpdate(profile.copy(transcriptionProviderId = p, transcriptionModelId = m)) },
                )
                ProviderModelSelectionEditor(
                    title = stringResource(R.string.provider_selection_ref_enhancement),
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
        title = { Text(stringResource(R.string.apps_language_add)) },
        text = {
            OutlinedTextField(
                value = draftLanguageCode,
                onValueChange = { draftLanguageCode = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.apps_language_code)) },
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                placeholder = { Text(stringResource(R.string.apps_language_code_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(draftLanguageCode) }, enabled = draftLanguageCode.trim().isNotBlank()) {
                Text(stringResource(R.string.apps_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.component_cancel))
            }
        },
    )
}
