package com.github.shekohex.whisperpp.ui.enhancement

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.DetailTopBar
import com.github.shekohex.whisperpp.ui.settings.common.SettingsGroup
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import java.util.Locale

@Composable
fun PromptsScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
    viewModel: PromptsViewModel = containerViewModel { container, handle -> PromptsViewModel(container, handle) },
) {
    val context = LocalContext.current
    val basePrompt by viewModel.basePrompt.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val promptProfiles by viewModel.promptProfiles.collectAsStateWithLifecycle()

    val promptText = draft ?: basePrompt.orEmpty()
    val hasChanges = draft != null && draft != basePrompt
    var pendingDeleteProfile by remember { mutableStateOf<PromptProfile?>(null) }

    Scaffold(
        topBar = {
            DetailTopBar(
                title = "Prompts & profiles",
                onBack = onBack,
                helpTopic = SettingsHelpTopic.PROMPTS_PROFILES,
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveBasePrompt {
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = hasChanges,
                    ) {
                        Text("Save")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.statusBars
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
                text = "Tap Save to apply base prompt edits. Profiles and app mappings update when you edit their entries.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsGroup(title = "Global base prompt") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = viewModel::setDraft,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 12,
                        label = { Text("Base prompt") },
                        placeholder = { Text("e.g. Improve clarity, fix punctuation, keep tone.") },
                    )
                    Text(
                        text = "Used as the foundation for enhancement prompting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SettingsGroup(title = "Prompt profiles") {
                ProfileList(
                    profiles = promptProfiles,
                    onAdd = { onNavigate(Destination.PromptEdit(id = null)) },
                    onEdit = { onNavigate(Destination.PromptEdit(id = it.id)) },
                    onDelete = { pendingDeleteProfile = it },
                )
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    pendingDeleteProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDeleteProfile = null },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("Delete profile?") },
            text = { Text("Delete \"${profile.name}\"? App mappings that reference it will be kept and will fallback until updated.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteProfile = null
                    viewModel.deleteProfile(profile.id) {
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProfile = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileList(
    profiles: List<PromptProfile>,
    onAdd: () -> Unit,
    onEdit: (PromptProfile) -> Unit,
    onDelete: (PromptProfile) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text("Add profile") },
            supportingContent = { Text("Name + optional prompt append") },
            leadingContent = { Icon(Icons.Default.Add, null) },
            modifier = Modifier.clickable(onClick = onAdd)
        )
        HorizontalDivider()
        if (profiles.isEmpty()) {
            ListItem(
                headlineContent = { Text("No profiles yet") },
                supportingContent = { Text("Create one to reuse prompt append text across apps.") },
                leadingContent = { Icon(Icons.Default.Bookmarks, null) },
            )
            return
        }
        val sortedProfiles = remember(profiles) { profiles.sortedBy { it.name.lowercase(Locale.getDefault()) } }
        sortedProfiles.forEachIndexed { index, profile ->
            ListItem(
                headlineContent = { Text(profile.name) },
                supportingContent = { Text(profile.promptAppend.ifBlank { "No append" }.take(80)) },
                leadingContent = { Icon(Icons.Default.Bookmarks, null) },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { onEdit(profile) }) {
                            Icon(Icons.Default.Edit, "Edit")
                        }
                        IconButton(onClick = { onDelete(profile) }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    }
                },
                modifier = Modifier.clickable { onEdit(profile) }
            )
            if (index != sortedProfiles.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}
