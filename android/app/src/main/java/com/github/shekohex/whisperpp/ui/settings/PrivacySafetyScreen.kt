package com.github.shekohex.whisperpp.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.navigation.NavHostController
import com.github.shekohex.whisperpp.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.COMMAND_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.DISCLOSURE_SHOWN_COMMAND_TEXT
import com.github.shekohex.whisperpp.DISCLOSURE_SHOWN_DICTATION_AUDIO
import com.github.shekohex.whisperpp.DISCLOSURE_SHOWN_ENHANCEMENT_TEXT
import com.github.shekohex.whisperpp.USE_CONTEXT
import com.github.shekohex.whisperpp.VERBOSE_NETWORK_LOGS_ENABLED
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.data.validateSelections
import com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatter
import com.github.shekohex.whisperpp.privacy.SendPolicyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class PrivacySafetyApp(
    val packageName: String,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySafetyScreen(dataStore: DataStore<Preferences>, navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sendPolicyRepository = remember { SendPolicyRepository(dataStore) }
    val settingsRepository = remember { SettingsRepository(dataStore) }
    val rules by sendPolicyRepository.getAllRulesFlow().collectAsState(initial = emptyMap())
    val providers by settingsRepository.providers.collectAsState(initial = emptyList())
    val settingsState by dataStore.data.collectAsState(initial = emptyPreferences())
    var searchQuery by remember { mutableStateOf("") }
    var manualPackageName by remember { mutableStateOf("") }

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
    val commandDisclosure = remember(commandProvider, validation.effective.commandText.modelId, useContext) {
        PrivacyDisclosureFormatter.disclosureForCommand(
            provider = commandProvider,
            selectedModelId = validation.effective.commandText.modelId,
            useContext = useContext,
        )
    }

    val launcherApps by produceState(initialValue = emptyList<PrivacySafetyApp>(), context) {
        value = withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager.queryIntentActivities(queryIntent, 0)
                .mapNotNull { resolveInfo ->
                    val packageName = resolveInfo.activityInfo?.packageName?.trim().orEmpty()
                    if (packageName.isEmpty()) {
                        null
                    } else {
                        val label = resolveInfo.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                        PrivacySafetyApp(
                            packageName = packageName,
                            label = label.ifEmpty { packageName },
                        )
                    }
                }
                .distinctBy { it.packageName }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
    }

    val visibleApps = remember(launcherApps, rules, searchQuery) {
        val installed = launcherApps.associateBy { it.packageName }
        val manualRules = rules.keys
            .asSequence()
            .filter { it.isNotBlank() && !installed.containsKey(it) }
            .map { packageName -> PrivacySafetyApp(packageName = packageName, label = packageName) }
            .toList()
        val merged = (launcherApps + manualRules)
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        val filter = searchQuery.trim().lowercase(Locale.US)
        if (filter.isBlank()) {
            merged
        } else {
            merged.filter {
                it.label.lowercase(Locale.US).contains(filter) ||
                    it.packageName.lowercase(Locale.US).contains(filter)
            }
        }
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("Privacy & Safety") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    SettingsHelpAction(SettingsScreen.PrivacySafety.route)
                },
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            SettingsGroup(title = "Disclosure") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DisclosureCard(dictationDisclosure)
                    DisclosureCard(enhancementDisclosure)
                    DisclosureCard(commandDisclosure)
                }
            }

            SettingsGroup(title = "Diagnostics") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Verbose network logs",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "When enabled, only redacted request headers are logged. Request and response payload bodies are never logged.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = verboseNetworkLogsEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    dataStore.edit { prefs ->
                                        prefs[VERBOSE_NETWORK_LOGS_ENABLED] = enabled
                                    }
                                }
                            },
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                dataStore.edit { prefs ->
                                    prefs[DISCLOSURE_SHOWN_DICTATION_AUDIO] = false
                                    prefs[DISCLOSURE_SHOWN_ENHANCEMENT_TEXT] = false
                                    prefs[DISCLOSURE_SHOWN_COMMAND_TEXT] = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset first-use disclosures")
                    }

                    Text(
                        text = "Changes apply immediately. Reset makes each first-use disclosure appear again the next time that mode runs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsGroup(title = "Per-app send policy") {
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
                            text = "Allow external sending is ON by default. Turn it OFF to block this app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search installed apps") },
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
                                text = "No matching apps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        visibleApps.forEachIndexed { index, app ->
                            val blocked = rules[app.packageName] == true
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Allow external sending",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Switch(
                                        checked = !blocked,
                                        onCheckedChange = { allowExternal ->
                                            scope.launch {
                                                sendPolicyRepository.setBlocked(app.packageName, !allowExternal)
                                            }
                                        },
                                    )
                                }
                            }
                            if (index < visibleApps.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            SettingsGroup(title = "Add rule by package name") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = manualPackageName,
                        onValueChange = { manualPackageName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Package name") },
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            val normalizedPackage = manualPackageName.trim()
                            if (normalizedPackage.isNotBlank()) {
                                scope.launch {
                                    sendPolicyRepository.setBlocked(normalizedPackage, true)
                                    manualPackageName = ""
                                }
                            }
                        },
                        enabled = manualPackageName.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Block package")
                    }
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun DisclosureCard(disclosure: PrivacyDisclosureFormatter.ModeDisclosure) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = disclosure.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = disclosure.dataSent,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            disclosure.endpoints.forEach { endpoint ->
                Text(
                    text = "Endpoint: ${endpoint.baseUrl}${endpoint.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = disclosure.contextLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
