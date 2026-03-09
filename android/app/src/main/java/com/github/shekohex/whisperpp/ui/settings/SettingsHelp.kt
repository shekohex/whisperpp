package com.github.shekohex.whisperpp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R

private data class SettingsHelpContent(
    val title: String,
    val summary: String,
    val tips: List<String>,
)

private fun canonicalSettingsHelpRoute(route: String): String {
    return when {
        route.startsWith("app_mapping_detail?") -> SettingsScreen.AppMappings.route
        route.startsWith("prompt_profile_edit?") -> SettingsScreen.PromptsProfiles.route
        route.startsWith("provider_edit?") -> SettingsScreen.Backend.route
        else -> route
    }
}

private fun settingsHelpContent(route: String): SettingsHelpContent {
    return when (canonicalSettingsHelpRoute(route)) {
        SettingsScreen.Main.route -> SettingsHelpContent(
            title = "Settings home",
            summary = "Start with the setup banner, then use the grouped cards to open a focused settings area.",
            tips = listOf(
                "Fix missing dictation or enhancement selections from Provider selections first.",
                "Each card shows one short status line so you can spot stale setup quickly.",
                "Import, export, and updates stay lower on the page so core setup remains first.",
            ),
        )

        SettingsScreen.Backend.route -> SettingsHelpContent(
            title = "Providers & models",
            summary = "Add providers here, then choose which ones Whisper++ should use elsewhere.",
            tips = listOf(
                "Provider selections controls the active STT and text defaults.",
                "Keep model lists accurate so status summaries and validation stay useful.",
                "If a provider is still selected, clear or reassign that selection before deleting it.",
            ),
        )

        SettingsScreen.ProviderSelections.route -> SettingsHelpContent(
            title = "Provider selections",
            summary = "Choose the default dictation, enhancement, and optional command models Whisper++ should use.",
            tips = listOf(
                "Pick a provider first, then choose one of its compatible models.",
                "Leave command override empty to inherit the enhancement selection.",
                "Per-language defaults can override these defaults for specific languages.",
            ),
        )

        SettingsScreen.LanguageDefaults.route -> SettingsHelpContent(
            title = "Per-language defaults",
            summary = "Create language-specific STT and text defaults when one global setup is not enough.",
            tips = listOf(
                "Add only the languages you actually switch between.",
                "Each language can override both dictation and enhancement defaults.",
                "If a provider or model disappears later, revisit the rule and update it.",
            ),
        )

        SettingsScreen.PromptsProfiles.route -> SettingsHelpContent(
            title = "Prompts & profiles",
            summary = "Shape how enhancement behaves globally, then reuse focused prompt profiles across apps.",
            tips = listOf(
                "Base prompt text applies everywhere unless a screen says it inherits something else.",
                "Profiles are reusable prompt append bundles for app mappings.",
                "Use App mappings when one app needs a different profile or provider override.",
            ),
        )

        SettingsScreen.AppMappings.route -> SettingsHelpContent(
            title = "App mappings",
            summary = "Map individual apps to prompt profiles and optional provider overrides.",
            tips = listOf(
                "Choose a profile when an app needs a different voice or cleanup style.",
                "Overrides are optional; leave them empty to inherit the global defaults.",
                "Manual package names are useful for apps that are not launchable from the picker.",
            ),
        )

        SettingsScreen.Presets.route -> SettingsHelpContent(
            title = "Transform presets",
            summary = "Set the default preset for post-dictation enhancement and command mode transforms.",
            tips = listOf(
                "Enhancement defaults apply after dictation when Smart Fix runs.",
                "Command defaults shape selected-text transforms before your spoken instruction adds detail.",
                "Changing a preset affects the next run; nothing is rewritten retroactively.",
            ),
        )

        SettingsScreen.Keyboard.route -> SettingsHelpContent(
            title = "Keyboard behavior",
            summary = "Tune how the IME behaves while recording, sending text, and giving feedback.",
            tips = listOf(
                "Automation toggles affect how fast Whisper++ moves from recording to insertion.",
                "UI extras keep typing behavior predictable across editors.",
                "Feedback toggles are local-only and can be adjusted without touching provider setup.",
            ),
        )

        SettingsScreen.PostProcessing.route -> SettingsHelpContent(
            title = "Smart Fix",
            summary = "Control legacy Smart Fix behavior and provider details used for post-processing.",
            tips = listOf(
                "Enable Smart Fix only when you want automatic cleanup after dictation.",
                "Custom prompt text should stay short and instruction-focused.",
                "Provider selections is the primary place for the new shared text-model defaults.",
            ),
        )

        SettingsScreen.PrivacySafety.route -> SettingsHelpContent(
            title = "Privacy & safety",
            summary = "Review what data is sent, manage blocked apps, and reset first-use disclosures.",
            tips = listOf(
                "Blocked apps keep external sending off even if dictation is available elsewhere.",
                "Verbose logs only include redacted headers; payload bodies are never logged.",
                "Use this screen when imported or changed selections need a trust review.",
            ),
        )

        SettingsScreen.BackupRestore.route -> SettingsHelpContent(
            title = "Backup & restore",
            summary = "Exports are password-encrypted only, and restores always go file pick to mode choice to password to preview before apply.",
            tips = listOf(
                "Provider credentials can be included in encrypted backups, so use a strong password you will remember.",
                "Merge mode lets you include or exclude whole categories before restoring anything.",
                "After restore, stay on this screen to review skipped items, cleared selections, and repair guidance.",
            ),
        )

        SettingsScreen.Analytics.route -> SettingsHelpContent(
            title = "Analytics dashboard",
            summary = "This screen stays local-only and compares your dictation wins against typing with a rolling seven-day view.",
            tips = listOf(
                "Time saved leads the dashboard, while named lifetime totals call out dictation minutes, WPM, and keystrokes saved.",
                "Sent versus cancelled sessions and raw versus final words stay here so settings home can remain compact.",
                "Reset clears the local dashboard in place on this device, including the visible seven-day trend scaffold.",
            ),
        )

        else -> SettingsHelpContent(
            title = "Settings help",
            summary = "Use the grouped settings screens to change one focused area at a time.",
            tips = listOf(
                "Setup-critical issues always belong at the top of settings home.",
                "Nested screens keep large configuration areas easier to scan.",
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHelpAction(route: String) {
    var showSheet by rememberSaveable(route) { mutableStateOf(false) }
    val help = settingsHelpContent(route)

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.TipsAndUpdates,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = help.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Text(
                    text = help.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    help.tips.forEach { tip ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = tip,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    IconButton(onClick = { showSheet = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = stringResource(R.string.settings_help_action),
        )
    }
}
