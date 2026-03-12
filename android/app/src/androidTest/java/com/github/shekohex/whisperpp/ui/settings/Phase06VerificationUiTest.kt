package com.github.shekohex.whisperpp.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.shekohex.whisperpp.data.CategoryPreview
import com.github.shekohex.whisperpp.data.ClearedSelection
import com.github.shekohex.whisperpp.data.ImportAnalysis
import com.github.shekohex.whisperpp.data.RestoreMode
import com.github.shekohex.whisperpp.data.RestoreRepairArea
import com.github.shekohex.whisperpp.data.RestoreRepairEntry
import com.github.shekohex.whisperpp.data.RestoreSelectionType
import com.github.shekohex.whisperpp.data.RestoreSummary
import com.github.shekohex.whisperpp.data.RestoreWarning
import com.github.shekohex.whisperpp.data.RestoreWarningKind
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_ACTIVE_SELECTIONS
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_MANIFEST
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_PRIVACY_SAFETY
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_PROMPTS_PROFILES
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_SCHEMA_VERSION
import com.github.shekohex.whisperpp.data.SettingsBackupPayload
import com.github.shekohex.whisperpp.data.SkippedImportItem
import com.github.shekohex.whisperpp.dataStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase06VerificationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsHome_showsBackupStatusAndRepairBanner() {
        setSettingsHomeContent(
            backupStatus = "Restore complete · 2 repair items",
            repairCount = 2,
        )

        composeRule.onNodeWithText("Setup essentials").assertIsDisplayed()
        composeRule.onNodeWithText("Personalization").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Behavior & safety").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Backup & restore").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("backup_restore_home_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Restore complete · 2 repair items").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_home_setup_banner").assertIsDisplayed()
        composeRule.onNodeWithText("Last restore still needs 2 repair steps.").assertIsDisplayed()
    }

    @Test
    fun importPreview_showsWarningsSkippedItemsAndDisabledConfirmWhenNothingSelected() {
        setImportPreviewContent(selectedMergeCategories = emptySet())

        composeRule.onNodeWithTag("backup_restore_preview_card").assertIsDisplayed()
        composeRule.onNodeWithText("Warnings").assertIsDisplayed()
        composeRule.onNodeWithText("Backup was created by a newer app version.").assertIsDisplayed()
        composeRule.onNodeWithText("Skipped invalid or unsupported items").assertIsDisplayed()
        composeRule.onNodeWithText("Provider credentials (legacy-provider): Unsupported credential format").assertIsDisplayed()
        composeRule.onNodeWithText("Provider credentials · 1 resulting item").assertIsDisplayed()
        composeRule.onNodeWithText("Prompts & profiles · 3 resulting items").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm restore for 0 categories").assertIsNotEnabled()
    }

    @Test
    fun restoreSummary_showsAppliedSkippedClearedAndRepairActions() {
        setRestoreSummaryContent()

        composeRule.onNodeWithTag("backup_restore_summary_card").assertIsDisplayed()
        composeRule.onNodeWithText("Applied categories").assertIsDisplayed()
        composeRule.onNodeWithText("Prompts & profiles").assertIsDisplayed()
        composeRule.onNodeWithText("Privacy & safety").assertIsDisplayed()
        composeRule.onNodeWithText("Skipped items").assertIsDisplayed()
        composeRule.onNodeWithText("Provider credentials (openai): Credential missing from secure restore source").assertIsDisplayed()
        composeRule.onNodeWithText("Cleared selections").assertIsDisplayed()
        composeRule.onNodeWithText("Enhancement: Imported provider is unavailable after restore").assertIsDisplayed()
        composeRule.onNodeWithText("Repair checklist").assertIsDisplayed()
        composeRule.onNodeWithText("Re-enter provider credentials for OpenAI.").assertIsDisplayed()
        composeRule.onNodeWithText("Choose a new enhancement selection.").assertIsDisplayed()
        composeRule.onNodeWithText("Open providers").assertIsDisplayed()
        composeRule.onNodeWithText("Open provider selections").assertIsDisplayed()
    }

    private fun setSettingsHomeContent(
        backupStatus: String,
        repairCount: Int,
    ) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.setContent {
            MaterialTheme {
                val navController = rememberNavController()
                LaunchedEffect(navController, backupStatus, repairCount) {
                    val homeEntry = navController.getBackStackEntry(SettingsScreen.Main.route)
                    homeEntry.savedStateHandle[BACKUP_RESTORE_HOME_STATUS_KEY] = backupStatus
                    homeEntry.savedStateHandle[BACKUP_RESTORE_REPAIR_COUNT_KEY] = repairCount
                }
                NavHost(navController = navController, startDestination = SettingsScreen.Main.route) {
                    composable(SettingsScreen.Main.route) {
                        SettingsHomeScreen(
                            dataStore = context.dataStore,
                            navController = navController,
                        )
                    }
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(backupStatus).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun setImportPreviewContent(selectedMergeCategories: Set<String>) {
        composeRule.setContent {
            var selectedCategories by mutableStateOf(selectedMergeCategories)
            MaterialTheme {
                ImportPreviewCard(
                    analysis = importAnalysisFixture(),
                    selectedMergeCategories = selectedCategories,
                    onToggleCategory = { categoryId ->
                        selectedCategories = if (selectedCategories.contains(categoryId)) {
                            selectedCategories - categoryId
                        } else {
                            selectedCategories + categoryId
                        }
                    },
                    onConfirmRestore = {},
                    categoryLabelById = SETTINGS_BACKUP_CATEGORY_MANIFEST.associateBy { it.id },
                    isApplyingImport = false,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun setRestoreSummaryContent() {
        composeRule.setContent {
            MaterialTheme {
                RestoreSummaryCard(
                    summary = restoreSummaryFixture(),
                    categoryLabelById = SETTINGS_BACKUP_CATEGORY_MANIFEST.associateBy { it.id },
                    onNavigate = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun importAnalysisFixture(): ImportAnalysis {
        val providerCredentials = SETTINGS_BACKUP_CATEGORY_MANIFEST.first {
            it.id == SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS
        }
        val promptsProfiles = SETTINGS_BACKUP_CATEGORY_MANIFEST.first {
            it.id == SETTINGS_BACKUP_CATEGORY_PROMPTS_PROFILES
        }
        val activeSelections = SETTINGS_BACKUP_CATEGORY_MANIFEST.first {
            it.id == SETTINGS_BACKUP_CATEGORY_ACTIVE_SELECTIONS
        }
        return ImportAnalysis(
            envelopeSchemaVersion = SETTINGS_BACKUP_SCHEMA_VERSION,
            payloadSchemaVersion = SETTINGS_BACKUP_SCHEMA_VERSION,
            backupAppVersionName = "1.0.0",
            exportedAtUtc = "2026-03-11T12:34:56Z",
            restoreMode = RestoreMode.MERGE,
            resolvedPayload = SettingsBackupPayload(),
            categoryPreviews = listOf(
                CategoryPreview(
                    categoryId = providerCredentials.id,
                    label = providerCredentials.label,
                    containsSensitiveContent = providerCredentials.containsSensitiveContent,
                    isAvailable = true,
                    selectable = true,
                    includedByDefault = true,
                    importedItemCount = 1,
                    existingItemCount = 0,
                    resultingItemCount = 1,
                ),
                CategoryPreview(
                    categoryId = promptsProfiles.id,
                    label = promptsProfiles.label,
                    containsSensitiveContent = promptsProfiles.containsSensitiveContent,
                    isAvailable = true,
                    selectable = true,
                    includedByDefault = true,
                    importedItemCount = 2,
                    existingItemCount = 1,
                    resultingItemCount = 3,
                    conflictKeys = listOf("default"),
                ),
                CategoryPreview(
                    categoryId = activeSelections.id,
                    label = activeSelections.label,
                    containsSensitiveContent = activeSelections.containsSensitiveContent,
                    isAvailable = true,
                    selectable = false,
                    includedByDefault = true,
                    importedItemCount = 1,
                    existingItemCount = 1,
                    resultingItemCount = 1,
                ),
            ),
            warnings = listOf(
                RestoreWarning(
                    kind = RestoreWarningKind.NEWER_APP_VERSION,
                    message = "Backup was created by a newer app version.",
                ),
            ),
            skippedItems = listOf(
                SkippedImportItem(
                    categoryId = providerCredentials.id,
                    itemKey = "legacy-provider",
                    reason = "Unsupported credential format",
                ),
            ),
        )
    }

    private fun restoreSummaryFixture(): RestoreSummary {
        return RestoreSummary(
            restoreMode = RestoreMode.MERGE,
            appliedCategories = listOf(
                SETTINGS_BACKUP_CATEGORY_PROMPTS_PROFILES,
                SETTINGS_BACKUP_CATEGORY_PRIVACY_SAFETY,
            ),
            skippedItems = listOf(
                SkippedImportItem(
                    categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS,
                    itemKey = "openai",
                    reason = "Credential missing from secure restore source",
                ),
            ),
            warnings = emptyList(),
            clearedSelections = listOf(
                ClearedSelection(
                    selectionType = RestoreSelectionType.ACTIVE_TEXT,
                    providerId = "text-provider",
                    modelId = "text-model",
                    reason = "Imported provider is unavailable after restore",
                ),
            ),
            repairChecklist = listOf(
                RestoreRepairEntry(
                    area = RestoreRepairArea.PROVIDER_CREDENTIALS,
                    providerId = "openai",
                    providerName = "OpenAI",
                    message = "Re-enter provider credentials for OpenAI.",
                ),
                RestoreRepairEntry(
                    area = RestoreRepairArea.ACTIVE_TEXT,
                    selectionType = RestoreSelectionType.ACTIVE_TEXT,
                    message = "Choose a new enhancement selection.",
                ),
            ),
        )
    }
}
