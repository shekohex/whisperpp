package com.github.shekohex.whisperpp.ui.apps

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.DetailTopBar
import com.github.shekohex.whisperpp.ui.settings.common.SettingsGroup
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.ui.settings.common.mergeAndFilterApps
import java.util.Locale

@Composable
fun AppMappingsScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val viewModel = containerViewModel { container, _ -> AppMappingsViewModel(container, appContext) }
    val appMappings by viewModel.appMappings.collectAsStateWithLifecycle()
    val promptProfiles by viewModel.promptProfiles.collectAsStateWithLifecycle()
    val launcherApps by viewModel.launcherApps.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var manualPackageName by rememberSaveable { mutableStateOf("") }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedPackages by remember { mutableStateOf(setOf<String>()) }
    var showBulkAssignDialog by remember { mutableStateOf(false) }

    val visibleApps = remember(launcherApps, appMappings, searchQuery) {
        mergeAndFilterApps(launcherApps, appMappings.map { it.packageName }, searchQuery)
    }

    fun toggleSelection(packageName: String) {
        selectedPackages = if (packageName in selectedPackages) selectedPackages - packageName else selectedPackages + packageName
    }

    Scaffold(
        topBar = {
            DetailTopBar(
                title = if (selectionMode) "${selectedPackages.size} selected" else "App mappings",
                onBack = onBack,
                helpTopic = SettingsHelpTopic.APP_MAPPINGS,
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = { showBulkAssignDialog = true }, enabled = selectedPackages.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Assign")
                        }
                        IconButton(onClick = {
                            selectionMode = false
                            selectedPackages = emptySet()
                        }) {
                            Icon(Icons.Default.Close, "Cancel")
                        }
                    } else {
                        IconButton(onClick = { selectionMode = true }) {
                            Icon(Icons.Default.DoneAll, "Multi-select")
                        }
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
            SettingsGroup(title = "Search") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search installed apps") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = manualPackageName,
                        onValueChange = { manualPackageName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Add by package name") },
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            viewModel.addManual(manualPackageName) { pkg ->
                                manualPackageName = ""
                                onNavigate(Destination.AppMappingDetail(pkg))
                            }
                        },
                        enabled = manualPackageName.trim().isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Add")
                    }
                }
            }

            SettingsGroup(title = "Installed apps") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (visibleApps.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No matching apps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val profileById = remember(promptProfiles) { promptProfiles.associateBy { it.id } }
                        val mappingByPackage = remember(appMappings) { appMappings.associateBy { it.packageName } }
                        visibleApps.forEachIndexed { index, app ->
                            val mappedProfileId = mappingByPackage[app.packageName]?.profileId?.trim().orEmpty()
                            val resolvedProfile = mappedProfileId.takeIf { it.isNotBlank() }?.let { profileById[it] }
                            AppMappingRow(
                                label = app.label,
                                packageName = app.packageName,
                                profileName = when {
                                    mappedProfileId.isBlank() -> "None"
                                    resolvedProfile != null -> resolvedProfile.name
                                    else -> "Missing"
                                },
                                missingProfile = mappedProfileId.isNotBlank() && resolvedProfile == null,
                                selectionMode = selectionMode,
                                selected = app.packageName in selectedPackages,
                                onClick = {
                                    if (selectionMode) {
                                        toggleSelection(app.packageName)
                                    } else {
                                        onNavigate(Destination.AppMappingDetail(app.packageName))
                                    }
                                },
                                onToggle = { toggleSelection(app.packageName) },
                            )
                            if (index < visibleApps.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (showBulkAssignDialog) {
        BulkAssignDialog(
            profiles = promptProfiles,
            onDismiss = { showBulkAssignDialog = false },
            onAssign = { profileId ->
                viewModel.assignProfile(selectedPackages, profileId) {
                    Toast.makeText(context, "Assigned", Toast.LENGTH_SHORT).show()
                    showBulkAssignDialog = false
                    selectionMode = false
                    selectedPackages = emptySet()
                }
            },
        )
    }
}

@Composable
private fun AppMappingRow(
    label: String,
    packageName: String,
    profileName: String,
    missingProfile: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(text = packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = profileName,
            style = MaterialTheme.typography.bodySmall,
            color = if (missingProfile) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (missingProfile) {
            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BulkAssignDialog(
    profiles: List<PromptProfile>,
    onDismiss: () -> Unit,
    onAssign: (String?) -> Unit,
) {
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    val sortedProfiles = remember(profiles) { profiles.sortedBy { it.name.lowercase(Locale.getDefault()) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListItem(
                    headlineContent = { Text("None") },
                    leadingContent = { RadioButton(selected = selectedProfileId == null, onClick = { selectedProfileId = null }) },
                    modifier = Modifier.clickable { selectedProfileId = null },
                )
                HorizontalDivider()
                sortedProfiles.forEach { profile ->
                    ListItem(
                        headlineContent = { Text(profile.name) },
                        supportingContent = { Text(profile.promptAppend.ifBlank { "No append" }.take(80)) },
                        leadingContent = {
                            RadioButton(selected = selectedProfileId == profile.id, onClick = { selectedProfileId = profile.id })
                        },
                        modifier = Modifier.clickable { selectedProfileId = profile.id },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAssign(selectedProfileId) }) {
                Text("Assign")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
