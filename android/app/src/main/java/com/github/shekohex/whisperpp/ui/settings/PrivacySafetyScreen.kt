package com.github.shekohex.whisperpp.ui.settings

import android.content.Intent
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.navigation.NavHostController
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
    val repository = remember { SendPolicyRepository(dataStore) }
    val rules by repository.getAllRulesFlow().collectAsState(initial = emptyMap())
    var searchQuery by remember { mutableStateOf("") }
    var manualPackageName by remember { mutableStateOf("") }

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
                }
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
                                                repository.setBlocked(app.packageName, !allowExternal)
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
                                    repository.setBlocked(normalizedPackage, true)
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
