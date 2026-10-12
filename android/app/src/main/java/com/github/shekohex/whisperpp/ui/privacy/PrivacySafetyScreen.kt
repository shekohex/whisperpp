package com.github.shekohex.whisperpp.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.AUTO_LEARN_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.USE_CONTEXT
import com.github.shekohex.whisperpp.data.PreferenceKeys.VERBOSE_NETWORK_LOGS_ENABLED
import com.github.shekohex.whisperpp.data.validateSelections
import com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatter
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.components.SectionCard
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.ui.settings.common.loadLauncherApps
import com.github.shekohex.whisperpp.ui.settings.common.mergeAndFilterApps

@Composable
fun PrivacySafetyScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit = {},
    viewModel: PrivacySafetyViewModel = defaultPrivacySafetyViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val launcherApps by viewModel.launcherApps.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val settingsState = prefs ?: emptyPreferences()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var manualPackageName by rememberSaveable { mutableStateOf("") }

    val useContext = settingsState[USE_CONTEXT] ?: false
    val verboseNetworkLogsEnabled = settingsState[VERBOSE_NETWORK_LOGS_ENABLED] ?: false

    val sttProviderId = settingsState[ACTIVE_STT_PROVIDER_ID].orEmpty()
    val sttModelId = settingsState[ACTIVE_STT_MODEL_ID].orEmpty()
    val textProviderId = settingsState[ACTIVE_TEXT_PROVIDER_ID].orEmpty()
    val textModelId = settingsState[ACTIVE_TEXT_MODEL_ID].orEmpty()
    val commandProviderId = settingsState[COMMAND_TEXT_PROVIDER_ID].orEmpty()
    val commandModelId = settingsState[COMMAND_TEXT_MODEL_ID].orEmpty()

    val validation = remember(providers, sttProviderId, sttModelId, textProviderId, textModelId, commandProviderId, commandModelId) {
        validateSelections(
            providers = providers,
            sttProviderId = sttProviderId,
            sttModelId = sttModelId,
            textProviderId = textProviderId,
            textModelId = textModelId,
            commandProviderId = commandProviderId,
            commandModelId = commandModelId,
        )
    }

    val dictationProvider = providers.find { it.id == validation.effective.stt.providerId }
    val enhancementProvider = providers.find { it.id == validation.effective.text.providerId }
    val commandProvider = providers.find { it.id == validation.effective.commandText.providerId }

    val dictationDisclosure = remember(dictationProvider, validation.effective.stt.modelId, useContext) {
        PrivacyDisclosureFormatter.disclosureForDictation(
            provider = dictationProvider,
            selectedModelId = validation.effective.stt.modelId,
            useContext = useContext,
        )
    }
    val enhancementDisclosure = remember(enhancementProvider, validation.effective.text.modelId, useContext) {
        PrivacyDisclosureFormatter.disclosureForEnhancement(
            provider = enhancementProvider,
            selectedModelId = validation.effective.text.modelId,
            useContext = useContext,
        )
    }
    val commandDisclosure = remember(
        dictationProvider,
        validation.effective.stt.modelId,
        commandProvider,
        validation.effective.commandText.modelId,
        useContext,
    ) {
        PrivacyDisclosureFormatter.disclosureForCommand(
            sttProvider = dictationProvider,
            sttModelId = validation.effective.stt.modelId,
            textProvider = commandProvider,
            textModelId = validation.effective.commandText.modelId,
            useContext = useContext,
        )
    }

    val visibleApps = remember(launcherApps, rules, searchQuery) {
        mergeAndFilterApps(launcherApps, rules.keys, searchQuery)
    }

    PageScaffold(
        title = stringResource(R.string.more_privacy_title),
        onBack = onBack,
        actions = { SettingsHelpAction(SettingsHelpTopic.PRIVACY_SAFETY) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            SectionCard(title = stringResource(R.string.privacy_disclosure_group)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DisclosureCard(
                        disclosure = dictationDisclosure,
                        modifier = Modifier.testTag("privacy_disclosure_card_dictation_audio"),
                    )
                    DisclosureCard(
                        disclosure = enhancementDisclosure,
                        modifier = Modifier.testTag("privacy_disclosure_card_enhancement_text"),
                    )
                    DisclosureCard(
                        disclosure = commandDisclosure,
                        modifier = Modifier.testTag("privacy_disclosure_card_command_text"),
                    )
                }
            }

            SectionCard(title = stringResource(R.string.privacy_diagnostics_group)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = verboseNetworkLogsEnabled,
                                role = Role.Switch,
                                onValueChange = viewModel::setVerboseNetworkLogs,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.about_verbose_logs_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(R.string.privacy_verbose_logs_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = verboseNetworkLogsEnabled, onCheckedChange = null)
                    }

                    OutlinedButton(
                        onClick = viewModel::resetDisclosures,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("privacy_reset_disclosures_button"),
                    ) {
                        Text(stringResource(R.string.privacy_reset_disclosures))
                    }

                    Text(
                        text = stringResource(R.string.privacy_reset_disclosures_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = stringResource(R.string.autolearn_privacy_group_title)) {
                AutoLearnPrivacyNote(
                    enabled = settingsState[AUTO_LEARN_ENABLED] ?: false,
                    onOpenSettings = { onNavigate(Destination.DictionarySettings) },
                )
            }

            SectionCard(title = stringResource(R.string.privacy_send_policy_group)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.privacy_send_policy_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("privacy_search_installed_apps_input"),
                        label = { Text(stringResource(R.string.privacy_search_apps)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                    )

                    if (visibleApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.privacy_no_matching_apps),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        visibleApps.forEachIndexed { index, app ->
                            val blocked = rules[app.packageName] == true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = !blocked,
                                        role = Role.Switch,
                                        onValueChange = { allowExternal ->
                                            viewModel.setBlocked(app.packageName, !allowExternal)
                                        },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = stringResource(R.string.privacy_allow_external_sending),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Switch(checked = !blocked, onCheckedChange = null)
                                }
                            }
                            if (index < visibleApps.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            SectionCard(title = stringResource(R.string.privacy_add_rule_group)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = manualPackageName,
                        onValueChange = { manualPackageName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("privacy_manual_package_input"),
                        label = { Text(stringResource(R.string.privacy_package_name)) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Button(
                        onClick = {
                            viewModel.setBlocked(manualPackageName, true)
                            manualPackageName = ""
                        },
                        enabled = manualPackageName.trim().isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("privacy_block_package_button"),
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.privacy_block_package))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DisclosureCard(
    disclosure: PrivacyDisclosureFormatter.ModeDisclosure,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val resources = LocalResources.current
            Text(
                text = stringResource(disclosure.title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(disclosure.dataSent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            disclosure.endpoints.forEach { endpoint ->
                Text(
                    text = PrivacyDisclosureFormatter.endpointLine(resources, endpoint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(disclosure.contextLine),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun defaultPrivacySafetyViewModel(): PrivacySafetyViewModel {
    val appContext = LocalContext.current.applicationContext
    return containerViewModel { container, _ ->
        PrivacySafetyViewModel(
            dataStore = container.settingsDataStore,
            settingsRepository = container.settingsRepository,
            sendPolicyRepository = container.sendPolicyRepository,
            loadApps = { loadLauncherApps(appContext) },
        )
    }
}
