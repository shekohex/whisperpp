package com.github.shekohex.whisperpp.data.dictionary

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.github.shekohex.whisperpp.core.text.Replacement
import com.github.shekohex.whisperpp.data.ProviderCredentialSink
import com.github.shekohex.whisperpp.data.ProviderCredentialSource
import com.github.shekohex.whisperpp.data.RestoreMode
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_DICTIONARY
import com.github.shekohex.whisperpp.data.SettingsBackupCrypto
import com.github.shekohex.whisperpp.data.SettingsBackupPayload
import com.github.shekohex.whisperpp.data.SettingsBackupRepository
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryRepositoryTest {

    private val gson = Gson()

    @Test
    fun replacements_addUpdateRemove() = runBlocking {
        val repository = DictionaryRepository(InMemoryDataStore(), gson)
        val rule = Replacement(listOf(" chat gpt ", "chatgpt", ""), " ChatGPT ")

        repository.addReplacement(rule)
        val stored = repository.replacements.first().single()
        assertEquals(Replacement(listOf("chat gpt", "chatgpt"), "ChatGPT"), stored)

        val updated = Replacement(listOf("chat gpt"), "ChatGPT-4")
        repository.updateReplacement(stored, updated)
        assertEquals(listOf(updated), repository.replacements.first())

        repository.removeReplacement(updated)
        assertEquals(emptyList<Replacement>(), repository.replacements.first())
    }

    @Test
    fun addReplacement_commaSeparatedOriginals_areSplit() = runBlocking {
        val repository = DictionaryRepository(InMemoryDataStore(), gson)
        repository.addReplacement(Replacement(listOf("gonna, gunna"), "going to"))
        assertEquals(listOf("gonna", "gunna"), repository.replacements.first().single().originals)
    }

    @Test
    fun addReplacement_takesOverConflictingOriginals() = runBlocking {
        val repository = DictionaryRepository(InMemoryDataStore(), gson)
        repository.addReplacement(Replacement(listOf("teh", "hte"), "the"))
        repository.addReplacement(Replacement(listOf("HTE"), "hate"))

        assertEquals(
            listOf(Replacement(listOf("teh"), "the"), Replacement(listOf("HTE"), "hate")),
            repository.replacements.first(),
        )
    }

    @Test
    fun vocabulary_isTrimmedAndCaseInsensitivelyUnique() = runBlocking {
        val repository = DictionaryRepository(InMemoryDataStore(), gson)
        repository.addVocabulary(" Kotlin ")
        repository.addVocabulary("kotlin")
        repository.addVocabulary("Compose")
        repository.addVocabulary(" ")
        assertEquals(listOf("Kotlin", "Compose"), repository.vocabulary.first())

        repository.removeVocabulary("KOTLIN")
        assertEquals(listOf("Compose"), repository.vocabulary.first())
    }

    @Test
    fun exportImport_roundTrips() = runBlocking {
        val source = DictionaryRepository(InMemoryDataStore(), gson)
        source.addReplacement(Replacement(listOf("teh"), "the"))
        source.addReplacement(Replacement(listOf("人工智能"), "AI"))
        source.setVocabulary(listOf("Whisper++", "VoiceInk"))
        val json = source.exportJson()

        val target = DictionaryRepository(InMemoryDataStore(), gson)
        target.addVocabulary("Old")
        target.importJson(json, DictionaryRepository.ImportMode.OVERWRITE).getOrThrow()

        assertEquals(source.snapshot(), target.snapshot())
    }

    @Test
    fun importMerge_keepsExistingAndImportedWinsConflicts() = runBlocking {
        val repository = DictionaryRepository(InMemoryDataStore(), gson)
        repository.addReplacement(Replacement(listOf("teh", "hte"), "the"))
        repository.addVocabulary("Kotlin")
        val imported = DictionaryBackup(
            replacements = listOf(Replacement(listOf("hte"), "hate")),
            vocabulary = listOf("kotlin", "Compose"),
        )

        repository.importJson(gson.toJson(imported)).getOrThrow()

        assertEquals(
            DictionaryBackup(
                replacements = listOf(Replacement(listOf("teh"), "the"), Replacement(listOf("hte"), "hate")),
                vocabulary = listOf("Kotlin", "Compose"),
            ),
            repository.snapshot(),
        )
    }

    @Test
    fun importInvalidJson_failsWithoutTouchingStorage() = runBlocking {
        val repository = DictionaryRepository(InMemoryDataStore(), gson)
        repository.addVocabulary("Kotlin")

        assertTrue(repository.importJson("not json").isFailure)
        assertTrue(repository.importJson("[1, 2]").isFailure)
        assertEquals(listOf("Kotlin"), repository.vocabulary.first())
    }

    @Test
    fun settingsBackup_carriesTheDictionaryCategory() = runBlocking {
        val sourceStore = InMemoryDataStore()
        val source = DictionaryRepository(sourceStore, gson)
        source.addReplacement(Replacement(listOf("teh"), "the"))
        source.addVocabulary("Whisper++")
        val envelope = backupRepository(sourceStore).exportEncryptedBackup(PASSWORD, "1.0")

        val targetStore = InMemoryDataStore()
        val backups = backupRepository(targetStore)
        val analysis = backups.analyzeEncryptedBackup(envelope, PASSWORD, RestoreMode.OVERWRITE)
        val summary = backups.applyImportAnalysis(analysis)

        assertTrue(SETTINGS_BACKUP_CATEGORY_DICTIONARY in summary.appliedCategories)
        assertEquals(source.snapshot(), DictionaryRepository(targetStore, gson).snapshot())
    }

    @Test
    fun settingsBackup_withoutDictionary_leavesItUntouched() = runBlocking {
        val store = InMemoryDataStore()
        val repository = DictionaryRepository(store, gson)
        repository.addVocabulary("Kotlin")
        // A backup written before WP-2.4 has no `dictionary` category.
        val legacy = JsonParser.parseString(gson.toJson(SettingsBackupPayload())).asJsonObject.apply { remove("dictionary") }
        val envelope = SettingsBackupCrypto.encryptUtf8(legacy.toString(), PASSWORD, "0.9", "2026-01-01T00:00:00Z")

        val backups = backupRepository(store)
        val analysis = backups.analyzeEncryptedBackup(envelope, PASSWORD, RestoreMode.OVERWRITE)
        val summary = backups.applyImportAnalysis(analysis)

        assertFalse(analysis.categoryPreviews.single { it.categoryId == SETTINGS_BACKUP_CATEGORY_DICTIONARY }.isAvailable)
        assertFalse(SETTINGS_BACKUP_CATEGORY_DICTIONARY in summary.appliedCategories)
        assertEquals(listOf("Kotlin"), repository.vocabulary.first())
    }

    private fun backupRepository(store: DataStore<Preferences>): SettingsBackupRepository {
        val credentials = object : ProviderCredentialSource, ProviderCredentialSink {
            override fun getProviderApiKey(providerId: String): String? = null
            override fun setProviderApiKey(providerId: String, apiKey: String) = Unit
            override fun clearProviderApiKey(providerId: String) = Unit
        }
        return SettingsBackupRepository(store, credentials, credentials, gson)
    }

    private class InMemoryDataStore : DataStore<Preferences> {
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
