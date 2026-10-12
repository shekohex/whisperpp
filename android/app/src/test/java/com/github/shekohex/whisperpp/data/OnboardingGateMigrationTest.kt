package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OnboardingGateMigrationTest {

    private val providerJson = """
        [{"id": "openai", "name": "OpenAI", "type": "OPENAI", "endpoint": "https://api.openai.com/v1", "authMode": "API_KEY",
          "models": [{"id": "whisper-1", "name": "Whisper", "kind": "STT"}]}]
    """.trimIndent()

    @Test
    fun existingInstallWithProvider_skipsOnboarding() = runBlocking {
        val dataStore = newDataStore()
        dataStore.edit { it[PreferenceKeys.PROVIDERS_JSON] = providerJson }

        SettingsRepository(dataStore).migrateOnboardingGateIfNeeded { false }

        assertEquals(true, dataStore.data.first()[PreferenceKeys.ONBOARDING_COMPLETED])
    }

    @Test
    fun existingInstallWithImeEnabled_skipsOnboarding() = runBlocking {
        val dataStore = newDataStore()

        SettingsRepository(dataStore).migrateOnboardingGateIfNeeded { true }

        assertTrue(SettingsRepository(dataStore).onboardingCompleted.first())
    }

    @Test
    fun freshInstall_keepsOnboarding() = runBlocking {
        val dataStore = newDataStore()

        SettingsRepository(dataStore).migrateOnboardingGateIfNeeded { false }

        assertNull(dataStore.data.first()[PreferenceKeys.ONBOARDING_COMPLETED])
        assertFalse(SettingsRepository(dataStore).onboardingCompleted.first())
    }

    @Test
    fun explicitValue_isNeverOverwritten() = runBlocking {
        val dataStore = newDataStore()
        dataStore.edit {
            it[PreferenceKeys.PROVIDERS_JSON] = providerJson
            it[PreferenceKeys.ONBOARDING_COMPLETED] = false
        }
        var imeChecked = false

        SettingsRepository(dataStore).migrateOnboardingGateIfNeeded {
            imeChecked = true
            true
        }

        assertEquals(false, dataStore.data.first()[PreferenceKeys.ONBOARDING_COMPLETED])
        assertFalse(imeChecked)
    }

    private fun newDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("onboarding-gate", ".preferences_pb").apply { delete(); deleteOnExit() }
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }
}
