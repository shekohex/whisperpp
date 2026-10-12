package com.github.shekohex.whisperpp.ui.backup

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.analytics.AnalyticsRepository
import com.github.shekohex.whisperpp.analytics.analyticsDataStore
import com.github.shekohex.whisperpp.core.permissions.AndroidSetupSystemChecks
import com.github.shekohex.whisperpp.data.BackupStatusStore
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
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.data.SkippedImportItem
import com.github.shekohex.whisperpp.dataStore
import com.github.shekohex.whisperpp.domain.setup.SetupStatusChecker
import com.github.shekohex.whisperpp.ui.home.HomeScreen
import com.github.shekohex.whisperpp.ui.home.HomeViewModel
import com.github.shekohex.whisperpp.ui.navigation.AppNavHost
import com.github.shekohex.whisperpp.ui.navigation.Destination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase06VerificationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_showsRestoreRepairIssue() {
        setHomeContent(backupStatus = "Restore complete · 2 repair items", repairCount = 2)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val repairs = context.resources.getQuantityString(R.plurals.home_issue_restore_repairs, 2, 2)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(repairs).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(repairs).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun backupRestore_isReachedFromMoreSettingsGroup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            MaterialTheme {
                AppNavHost(
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    startDestination = Destination.Home,
                    launchRequest = null,
                    onLaunchRequestHandled = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.nav_more)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.more_group_settings)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.more_backup_title)).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(context.getString(R.string.more_backup_title))
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun importPreview_showsWarningsSkippedItemsAndDisabledConfirmWhenNothingSelected() {
        setImportPreviewContent(selectedMergeCategories = emptySet())

        composeRule.onNodeWithTag("backup_restore_preview_card").assertIsDisplayed()
        composeRule.onNodeWithText("Warnings").assertIsDisplayed()
        composeRule.onNodeWithText("This backup was made with a newer app version (1.0.0).").assertIsDisplayed()
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
        composeRule.onNodeWithText("Enhancement: The restored provider or model is not available on this device.").assertIsDisplayed()
        composeRule.onNodeWithText("Repair checklist").assertIsDisplayed()
        composeRule.onNodeWithText("Re-enter the API key for OpenAI before using it.").assertIsDisplayed()
        composeRule.onNodeWithText("Enhancement: choose a new provider and model.").assertIsDisplayed()
        composeRule.onNodeWithText("Open providers").assertIsDisplayed()
        composeRule.onNodeWithText("Open default models").assertIsDisplayed()
    }

    private fun setHomeContent(
        backupStatus: String,
        repairCount: Int,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = HomeViewModel(
            dataStore = context.dataStore,
            settingsRepository = SettingsRepository(context.dataStore),
            analyticsRepository = AnalyticsRepository(context.analyticsDataStore),
            backupStatusStore = BackupStatusStore().apply { update(backupStatus, repairCount) },
            setupStatusChecker = setupStatusChecker(context),
        )
        composeRule.setContent {
            MaterialTheme {
                HomeScreen(onNavigate = {}, viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()
    }

    private fun setImportPreviewContent(selectedMergeCategories: Set<String>) {
        composeRule.setContent {
            var selectedCategories by remember { mutableStateOf(selectedMergeCategories) }
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

    private fun setupStatusChecker(context: android.content.Context): SetupStatusChecker {
        val settingsRepository = SettingsRepository(context.dataStore)
        return SetupStatusChecker(
            systemChecks = AndroidSetupSystemChecks(context),
            providers = settingsRepository.providers,
            preferences = context.dataStore.data,
            hasCredential = { false },
        )
    }
}
