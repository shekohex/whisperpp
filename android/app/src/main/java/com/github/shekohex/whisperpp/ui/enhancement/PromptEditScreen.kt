package com.github.shekohex.whisperpp.ui.enhancement

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.ui.settings.common.DetailTopBar
import com.github.shekohex.whisperpp.ui.settings.common.SettingsGroup
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel

@Composable
fun PromptEditScreen(
    profileId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = containerViewModel { container, handle -> PromptEditViewModel(container, handle, profileId) }
    val name by viewModel.name.collectAsStateWithLifecycle()
    val append by viewModel.append.collectAsStateWithLifecycle()
    val notFound by viewModel.notFound.collectAsStateWithLifecycle()

    LaunchedEffect(notFound) {
        if (notFound) {
            Toast.makeText(context, "Profile not found", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            DetailTopBar(
                title = if (viewModel.isNew) "New profile" else "Edit profile",
                onBack = onBack,
                helpTopic = SettingsHelpTopic.PROMPTS_PROFILES,
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.save {
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        enabled = name.trim().isNotBlank(),
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
                text = "Save stores this profile immediately so it can be reused in app mappings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsGroup(title = "Profile") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = viewModel::setName,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Name") },
                    )
                    OutlinedTextField(
                        value = append,
                        onValueChange = viewModel::setAppend,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 10,
                        label = { Text("Prompt append (optional)") },
                    )
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
