package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.github.shekohex.whisperpp.data.history.HistoryBackup
import com.github.shekohex.whisperpp.data.history.HistoryBackupEntry
import com.github.shekohex.whisperpp.data.history.HistoryBackupSource
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupHistoryTest {
    private val gson = Gson()

    @Test
    fun historyCategory_isOptIn_inManifestAndExport() = runBlocking {
        val manifest = SETTINGS_BACKUP_CATEGORY_MANIFEST.single { it.id == SETTINGS_BACKUP_CATEGORY_HISTORY }
        assertFalse(manifest.includedByDefault)
        assertTrue(manifest.containsSensitiveContent)

        val source = FakeHistorySource(listOf(HistoryBackupEntry(createdAt = 1, rawText = "local")))
        val repository = repository(source)

        val plain = JsonParser.parseString(decrypt(repository.exportEncryptedBackup(PASSWORD, "1.0"))).asJsonObject
        assertFalse(plain.has("history"))

        val withHistory = JsonParser.parseString(decrypt(repository.exportEncryptedBackup(PASSWORD, "1.0", includeHistory = true))).asJsonObject
        val entries = withHistory.getAsJsonObject("history").getAsJsonArray("entries")
        assertEquals(1, entries.size())
        assertFalse(entries[0].asJsonObject.has("audioPath"))
    }

    @Test
    fun import_skipsHistoryUnlessSelected() = runBlocking {
        val source = FakeHistorySource(listOf(HistoryBackupEntry(createdAt = 1, rawText = "local")))
        val repository = repository(source)
        val envelope = envelope(
            SettingsBackupPayload(history = HistoryBackup(entries = listOf(HistoryBackupEntry(createdAt = 2, rawText = "imported")))),
        )

        val merge = repository.analyzeEncryptedBackup(envelope, PASSWORD, RestoreMode.MERGE)
        val preview = merge.categoryPreviews.single { it.categoryId == SETTINGS_BACKUP_CATEGORY_HISTORY }
        assertTrue(preview.isAvailable)
        assertFalse(preview.includedByDefault)
        assertEquals(2, preview.resultingItemCount)

        repository.applyImportAnalysis(merge)
        assertEquals(listOf("local"), source.entries.map { it.rawText })

        val overwrite = repository.analyzeEncryptedBackup(envelope, PASSWORD, RestoreMode.OVERWRITE)
        val summary = repository.applyImportAnalysis(overwrite)
        assertFalse(SETTINGS_BACKUP_CATEGORY_HISTORY in summary.appliedCategories)
        assertEquals(listOf("local"), source.entries.map { it.rawText })

        repository.applyImportAnalysis(merge, includedCategoryIds = setOf(SETTINGS_BACKUP_CATEGORY_HISTORY))
        assertEquals(setOf("local", "imported"), source.entries.map { it.rawText }.toSet())
        assertFalse(source.lastReplace)

        repository.applyImportAnalysis(overwrite, includedCategoryIds = setOf(SETTINGS_BACKUP_CATEGORY_HISTORY))
        assertEquals(listOf("imported"), source.entries.map { it.rawText })
        assertTrue(source.lastReplace)
    }

    private fun repository(source: HistoryBackupSource) = SettingsBackupRepository(
        dataStore = MutablePreferencesDataStore(),
        credentialSource = object : ProviderCredentialSource {
            override fun getProviderApiKey(providerId: String): String? = null
        },
        gson = gson,
        timestampProvider = { "2026-10-12T00:00:00Z" },
        historySource = source,
    )

    private fun envelope(payload: SettingsBackupPayload) = SettingsBackupCrypto.encryptUtf8(
        plaintext = gson.toJson(payload),
        password = PASSWORD,
        appVersionName = "1.0.0",
        exportedAtUtc = "2026-10-12T00:00:00Z",
    )

    private fun decrypt(envelope: SettingsBackupEnvelope) = SettingsBackupCrypto.decryptUtf8(envelope, PASSWORD)

    private class FakeHistorySource(initial: List<HistoryBackupEntry>) : HistoryBackupSource {
        var entries: List<HistoryBackupEntry> = initial
        var lastReplace = false

        override suspend fun exportBackup() = HistoryBackup(entries = entries)

        override suspend fun importBackup(backup: HistoryBackup, replace: Boolean): Int {
            lastReplace = replace
            val base = if (replace) emptyList() else entries
            val added = backup.entries.filterNot { incoming -> base.any { it.createdAt == incoming.createdAt && it.rawText == incoming.rawText } }
            entries = base + added
            return added.size
        }
    }

    private class MutablePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private companion object {
        const val PASSWORD = "backup-password"
    }
}
