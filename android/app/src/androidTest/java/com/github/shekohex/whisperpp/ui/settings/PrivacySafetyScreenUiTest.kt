package com.github.shekohex.whisperpp.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.shekohex.whisperpp.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.COMMAND_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.DISCLOSURE_SHOWN_COMMAND_TEXT
import com.github.shekohex.whisperpp.DISCLOSURE_SHOWN_DICTATION_AUDIO
import com.github.shekohex.whisperpp.DISCLOSURE_SHOWN_ENHANCEMENT_TEXT
import com.github.shekohex.whisperpp.USE_CONTEXT
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ModelKind
import com.github.shekohex.whisperpp.data.PROVIDERS_JSON
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacySafetyScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disclosureCards_renderAndManualPolicyControlsRemainReachable() {
        val dataStore = seededPrivacySafetyDataStore()
        setPrivacySafetyContent(dataStore)

        composeRule.onNodeWithTag("privacy_disclosure_card_dictation_audio").assertIsDisplayed()
        composeRule.onNodeWithTag("privacy_disclosure_card_enhancement_text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("privacy_disclosure_card_command_text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Command mode").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Instruction audio transcription: https://stt.example.com/v1/audio/transcriptions")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Text transform: https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("privacy_search_installed_apps_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("privacy_manual_package_input").performScrollTo().performTextInput("com.example.manual")
        composeRule.onNodeWithTag("privacy_block_package_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("com.example.manual").performScrollTo().assertIsDisplayed()
            }.isSuccess
        }

        composeRule.onNodeWithTag("privacy_search_installed_apps_input").performScrollTo().performTextInput("manual")
        composeRule.onNodeWithText("com.example.manual").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun resetDisclosureAction_clearsAllThreeModeFlags() {
        val dataStore = seededPrivacySafetyDataStore()
        setPrivacySafetyContent(dataStore)

        composeRule.onNodeWithTag("privacy_reset_disclosures_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                val prefs = dataStore.data.first()
                !(prefs[DISCLOSURE_SHOWN_DICTATION_AUDIO] ?: false) &&
                    !(prefs[DISCLOSURE_SHOWN_ENHANCEMENT_TEXT] ?: false) &&
                    !(prefs[DISCLOSURE_SHOWN_COMMAND_TEXT] ?: false)
            }
        }

        runBlocking {
            val prefs = dataStore.data.first()
            assertFalse(prefs[DISCLOSURE_SHOWN_DICTATION_AUDIO] ?: false)
            assertFalse(prefs[DISCLOSURE_SHOWN_ENHANCEMENT_TEXT] ?: false)
            assertFalse(prefs[DISCLOSURE_SHOWN_COMMAND_TEXT] ?: false)
        }
    }

    private fun setPrivacySafetyContent(dataStore: DataStore<Preferences>) {
        composeRule.setContent {
            MaterialTheme {
                PrivacySafetyScreen(
                    dataStore = dataStore,
                    navController = rememberNavController(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun seededPrivacySafetyDataStore(): MutablePreferencesDataStore {
        val dataStore = MutablePreferencesDataStore()
        runBlocking {
            dataStore.edit { prefs ->
                prefs[PROVIDERS_JSON] = Gson().toJson(seedProviders())
                prefs[ACTIVE_STT_PROVIDER_ID] = "stt"
                prefs[ACTIVE_STT_MODEL_ID] = "whisper-1"
                prefs[ACTIVE_TEXT_PROVIDER_ID] = "text"
                prefs[ACTIVE_TEXT_MODEL_ID] = "gpt-4o-mini"
                prefs[COMMAND_TEXT_PROVIDER_ID] = "command"
                prefs[COMMAND_TEXT_MODEL_ID] = "gemini-1.5-pro"
                prefs[USE_CONTEXT] = true
                prefs[DISCLOSURE_SHOWN_DICTATION_AUDIO] = true
                prefs[DISCLOSURE_SHOWN_ENHANCEMENT_TEXT] = true
                prefs[DISCLOSURE_SHOWN_COMMAND_TEXT] = true
            }
        }
        return dataStore
    }

    private fun seedProviders(): List<ServiceProvider> {
        return listOf(
            ServiceProvider(
                id = "stt",
                name = "STT",
                type = ProviderType.OPENAI,
                endpoint = "https://stt.example.com/v1",
                models = listOf(ModelConfig(id = "whisper-1", name = "Whisper 1", kind = ModelKind.STT)),
            ),
            ServiceProvider(
                id = "text",
                name = "Text",
                type = ProviderType.OPENAI,
                endpoint = "https://text.example.com/v1",
                models = listOf(ModelConfig(id = "gpt-4o-mini", name = "GPT-4o mini", kind = ModelKind.TEXT)),
            ),
            ServiceProvider(
                id = "command",
                name = "Gemini",
                type = ProviderType.GEMINI,
                endpoint = "https://generativelanguage.googleapis.com/v1beta",
                models = listOf(ModelConfig(id = "gemini-1.5-pro", name = "Gemini 1.5 Pro", kind = ModelKind.MULTIMODAL)),
            ),
        )
    }

    private class MutablePreferencesDataStore(
        initialPreferences: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initialPreferences)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
