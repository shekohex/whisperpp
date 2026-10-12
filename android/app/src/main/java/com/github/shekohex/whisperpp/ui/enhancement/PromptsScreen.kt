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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
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

    PageScaffold(
        title = stringResource(R.string.enhancement_prompts_link_title),
        onBack = onBack,
        actions = {
            SettingsHelpAction(SettingsHelpTopic.PROMPTS_PROFILES)
            TextButton(
                onClick = {
                    viewModel.saveBasePrompt {
                        Toast.makeText(context, R.string.prompts_saved, Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = hasChanges,
            ) {
                Text(stringResource(R.string.prompts_save))
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
                text = stringResource(R.string.prompts_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionCard(title = stringResource(R.string.prompts_base_group)) {
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
                        label = { Text(stringResource(R.string.prompts_base_label)) },
                        placeholder = { Text(stringResource(R.string.prompts_base_hint)) },
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    )
                    Text(
                        text = stringResource(R.string.prompts_base_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SectionCard(title = stringResource(R.string.prompts_profiles_group)) {
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
            title = { Text(stringResource(R.string.prompts_delete_title)) },
            text = { Text(stringResource(R.string.prompts_delete_message, profile.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteProfile = null
                    viewModel.deleteProfile(profile.id) {
                        Toast.makeText(context, R.string.prompts_deleted, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.prompts_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProfile = null }) {
                    Text(stringResource(R.string.component_cancel))
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
            headlineContent = { Text(stringResource(R.string.prompts_add)) },
            supportingContent = { Text(stringResource(R.string.prompts_add_summary)) },
            leadingContent = { Icon(Icons.Default.Add, null) },
            modifier = Modifier.clickable(role = Role.Button, onClick = onAdd)
        )
        HorizontalDivider()
        if (profiles.isEmpty()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.prompts_empty_title)) },
                supportingContent = { Text(stringResource(R.string.prompts_empty_body)) },
                leadingContent = { Icon(Icons.Default.Bookmarks, null) },
            )
            return
        }
        val sortedProfiles = remember(profiles) { profiles.sortedBy { it.name.lowercase(Locale.getDefault()) } }
        val noAppend = stringResource(R.string.prompts_no_append)
        sortedProfiles.forEachIndexed { index, profile ->
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
                leadingContent = { Icon(Icons.Default.Bookmarks, null) },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onEdit(profile) }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.prompts_edit_named, profile.name))
                        }
                        IconButton(onClick = { onDelete(profile) }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.prompts_delete_named, profile.name))
                        }
                    }
                },
                modifier = Modifier.clickable(role = Role.Button) { onEdit(profile) }
            )
            if (index != sortedProfiles.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}
