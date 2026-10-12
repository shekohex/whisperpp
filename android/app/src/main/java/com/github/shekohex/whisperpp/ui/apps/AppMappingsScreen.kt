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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.components.SectionCard
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
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
    val resources = LocalResources.current
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

    PageScaffold(
        title = if (selectionMode) {
            pluralStringResource(R.plurals.apps_selected_count, selectedPackages.size, selectedPackages.size)
        } else {
            stringResource(R.string.modes_app_mappings_title)
        },
        onBack = onBack,
        actions = {
            SettingsHelpAction(SettingsHelpTopic.APP_MAPPINGS)
            if (selectionMode) {
                IconButton(onClick = { showBulkAssignDialog = true }, enabled = selectedPackages.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.apps_assign))
                }
                IconButton(onClick = {
                    selectionMode = false
                    selectedPackages = emptySet()
                }) {
                    Icon(Icons.Default.Close, stringResource(R.string.apps_cancel_selection))
                }
            } else {
                IconButton(onClick = { selectionMode = true }) {
                    Icon(Icons.Default.DoneAll, stringResource(R.string.apps_multi_select))
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            SectionCard(title = stringResource(R.string.apps_search_group)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.apps_search_label)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = manualPackageName,
                        onValueChange = { manualPackageName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.apps_add_package_label)) },
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
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
                        Text(stringResource(R.string.apps_add))
                    }
                }
            }

            SectionCard(title = stringResource(R.string.apps_installed_group)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (visibleApps.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.apps_no_matching),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val profileById = remember(promptProfiles) { promptProfiles.associateBy { it.id } }
                        val mappingByPackage = remember(appMappings) { appMappings.associateBy { it.packageName } }
                        val noneLabel = stringResource(R.string.apps_profile_none)
                        val missingLabel = stringResource(R.string.apps_profile_missing)
                        visibleApps.forEachIndexed { index, app ->
                            val mappedProfileId = mappingByPackage[app.packageName]?.profileId?.trim().orEmpty()
                            val resolvedProfile = mappedProfileId.takeIf { it.isNotBlank() }?.let { profileById[it] }
                            AppMappingRow(
                                label = app.label,
                                packageName = app.packageName,
                                profileName = when {
                                    mappedProfileId.isBlank() -> noneLabel
                                    resolvedProfile != null -> resolvedProfile.name
                                    else -> missingLabel
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
                val count = selectedPackages.size
                viewModel.assignProfile(selectedPackages, profileId) {
                    val message = resources.getQuantityString(R.plurals.apps_assigned, count, count)
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
    val rowModifier = if (selectionMode) {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(rowModifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content),
                fontWeight = FontWeight.Medium,
            )
            Text(text = packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = profileName,
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
            modifier = Modifier.widthIn(max = 120.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (missingProfile) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (missingProfile) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = stringResource(R.string.apps_profile_missing_description),
                tint = MaterialTheme.colorScheme.error,
            )
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
    val noAppend = stringResource(R.string.apps_no_append)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.apps_assign_profile_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.apps_profile_none)) },
                    leadingContent = { RadioButton(selected = selectedProfileId == null, onClick = null) },
                    modifier = Modifier.selectable(
                        selected = selectedProfileId == null,
                        role = Role.RadioButton,
                        onClick = { selectedProfileId = null },
                    ),
                )
                HorizontalDivider()
                sortedProfiles.forEach { profile ->
                    ListItem(
                        headlineContent = {
                            Text(profile.name, style = LocalTextStyle.current.copy(textDirection = TextDirection.Content))
                        },
                        supportingContent = {
                            Text(
                                profile.promptAppend.ifBlank { noAppend }.take(80),
                                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                            )
                        },
                        leadingContent = { RadioButton(selected = selectedProfileId == profile.id, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = selectedProfileId == profile.id,
                            role = Role.RadioButton,
                            onClick = { selectedProfileId = profile.id },
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAssign(selectedProfileId) }) {
                Text(stringResource(R.string.apps_assign))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.component_cancel))
            }
        },
    )
}
