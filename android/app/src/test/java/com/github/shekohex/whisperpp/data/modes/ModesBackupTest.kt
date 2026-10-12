package com.github.shekohex.whisperpp.data.modes

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.github.shekohex.whisperpp.data.ModesBackup
import com.github.shekohex.whisperpp.data.PreferenceKeys
import com.github.shekohex.whisperpp.data.ProviderCredentialSource
import com.github.shekohex.whisperpp.data.RestoreMode
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_MODES
import com.github.shekohex.whisperpp.data.SettingsBackupCrypto
import com.github.shekohex.whisperpp.data.SettingsBackupPayload
import com.github.shekohex.whisperpp.data.SettingsBackupRepository
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModesBackupTest {
    private val gson = Gson()
    private val noCredentials = object : ProviderCredentialSource {
        override fun getProviderApiKey(providerId: String): String? = null
    }

    private val local = listOf(
        ModeSeeds.DICTATION,
        Mode(id = "chat", name = "Local chat", appPackages = listOf("org.telegram", "com.whatsapp")),
        Mode(id = "local-only", name = "Local only"),
    )
    private val imported = listOf(
        Mode(id = "chat", name = "Imported chat", sttSelection = Mode.Selection("p", "m"), appPackages = listOf("org.telegram")),
        Mode(id = "work", name = "Work", isDefault = true, appPackages = listOf("com.whatsapp"), promptId = "work-prompt"),
    )

    private fun store() = InMemoryPreferencesStore(
        mutablePreferencesOf(
            PreferenceKeys.MODES_V1 to gson.toJson(local),
            PreferenceKeys.SELECTED_MODE_ID to "local-only",
        )
    )

    private fun repository(store: InMemoryPreferencesStore) =
        SettingsBackupRepository(dataStore = store, credentialSource = noCredentials, gson = gson)

    private fun envelope(modes: List<Mode>) = SettingsBackupCrypto.encryptUtf8(
        plaintext = gson.toJson(SettingsBackupPayload(modes = ModesBackup(modes))),
        password = PASSWORD,
        appVersionName = "1.0.0",
        exportedAtUtc = "2026-10-12T00:00:00Z",
    )

    @Test
    fun export_containsStoredModes_only() = runBlocking {
        assertEquals(local, repository(store()).buildExportPayload().modes.modes)
        assertTrue(repository(InMemoryPreferencesStore()).buildExportPayload().modes.modes.isEmpty())
    }

    @Test
    fun merge_byId_importedWinsDefaultAndPackages_andMarksMigrated() = runBlocking {
        val store = store()
        val repository = repository(store)
        val analysis = repository.analyzeEncryptedBackup(envelope(imported), PASSWORD, RestoreMode.MERGE)
        val preview = analysis.categoryPreviews.single { it.categoryId == SETTINGS_BACKUP_CATEGORY_MODES }
        assertEquals(listOf("chat"), preview.conflictKeys)

        repository.applyImportAnalysis(analysis, setOf(SETTINGS_BACKUP_CATEGORY_MODES))

        val modes = ModesRepository(store).modes.first().associateBy { it.id }
        assertEquals(listOf(ModeSeeds.DICTATION_ID, "chat", "local-only", "work"), modes.keys.toList())
        assertEquals("Imported chat", modes.getValue("chat").name)
        assertEquals(listOf("org.telegram"), modes.getValue("chat").appPackages)
        assertEquals(listOf("com.whatsapp"), modes.getValue("work").appPackages)
        assertEquals(listOf("work"), modes.values.filter { it.isDefault }.map { it.id })
        assertEquals("work-prompt", modes.getValue("work").promptId)
        assertEquals(true, store.current[PreferenceKeys.MODES_MIGRATED])
        assertEquals("local-only", store.current[PreferenceKeys.SELECTED_MODE_ID])
    }

    @Test
    fun overwrite_replacesModes_andClearsAStaleKeyboardSelection() = runBlocking {
        val store = store()
        val repository = repository(store)
        val analysis = repository.analyzeEncryptedBackup(envelope(imported), PASSWORD, RestoreMode.OVERWRITE)

        repository.applyImportAnalysis(analysis)

        assertEquals(listOf("chat", "work"), ModesRepository(store).modes.first().map { it.id })
        assertNull(store.current[PreferenceKeys.SELECTED_MODE_ID])
    }

    @Test
    fun backupWithoutModes_leavesModesUntouched() = runBlocking {
        val store = store()
        val repository = repository(store)
        val analysis = repository.analyzeEncryptedBackup(envelope(emptyList()), PASSWORD, RestoreMode.OVERWRITE)

        repository.applyImportAnalysis(analysis)

        assertEquals(local, ModesRepository(store).modes.first())
        assertNull(store.current[PreferenceKeys.MODES_MIGRATED])
    }

    private companion object {
        const val PASSWORD = "backup-password"
    }
}
