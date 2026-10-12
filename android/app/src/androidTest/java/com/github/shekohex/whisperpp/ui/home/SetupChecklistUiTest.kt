package com.github.shekohex.whisperpp.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.shekohex.whisperpp.domain.setup.SetupStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupChecklistUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun checklist_visibleWhileSomethingIsMissing_hiddenWhenAllGreen() {
        var status by mutableStateOf(SetupStatus(imeEnabled = true, imeSelected = false, micGranted = true))
        composeRule.setContent {
            MaterialTheme {
                SetupChecklist(
                    status = status,
                    micPermanentlyDenied = false,
                    hasProviders = false,
                    onEnableKeyboard = {},
                    onSelectKeyboard = {},
                    onMic = {},
                    onProvider = {},
                )
            }
        }

        composeRule.onNodeWithTag(SETUP_CHECKLIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Not selected").assertIsDisplayed()
        composeRule.onNodeWithText("Not set up").assertIsDisplayed()

        status = SetupStatus(imeEnabled = true, imeSelected = true, micGranted = true, sttProviderConfigured = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETUP_CHECKLIST_TAG).assertDoesNotExist()
    }

    @Test
    fun checklist_distinguishesEnabledFromSelected() {
        composeRule.setContent {
            MaterialTheme {
                SetupChecklist(
                    status = SetupStatus(imeEnabled = true, imeSelected = false),
                    micPermanentlyDenied = false,
                    hasProviders = true,
                    onEnableKeyboard = {},
                    onSelectKeyboard = {},
                    onMic = {},
                    onProvider = {},
                )
            }
        }

        composeRule.onNodeWithText("Enabled").assertIsDisplayed()
        composeRule.onNodeWithText("Not selected").assertIsDisplayed()
    }
}
