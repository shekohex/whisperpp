package com.github.shekohex.whisperpp.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsRepositoryProviderPersistenceTest {

    @Test
    fun upsertProvider_persistsNewProviderWithModels() = runBlocking {
        val repository = createRepository()
        val provider = ServiceProvider(
            id = "provider-1",
            name = "OpenAI",
            type = ProviderType.OPENAI,
            endpoint = "https://api.openai.com/v1",
            models = listOf(
                ModelConfig(id = "gpt-4o", name = "GPT-4o", kind = ModelKind.TEXT),
                ModelConfig(id = "whisper-1", name = "Whisper 1", kind = ModelKind.STT),
            ),
        )

        repository.upsertProvider(provider)

        val savedProviders = repository.providers.first()
        assertEquals(1, savedProviders.size)
        assertEquals(provider, savedProviders.single())
    }

    @Test
    fun upsertProvider_afterImportedModelsAndExplicitSave_keepsProviderPersisted() = runBlocking {
        val repository = createRepository()
        val initial = ServiceProvider(
            id = "provider-1",
            name = "OpenAI",
            type = ProviderType.OPENAI,
            endpoint = "https://api.openai.com/v1",
            models = emptyList(),
        )
        val imported = initial.copy(
            models = listOf(
                ModelConfig(id = "gpt-4o", name = "GPT-4o", kind = ModelKind.TEXT),
                ModelConfig(id = "gpt-4.1-mini", name = "GPT-4.1 Mini", kind = ModelKind.TEXT),
            ),
        )
        val saved = imported.copy(
            name = "OpenAI Primary",
            timeout = 20_000,
        )

        repository.upsertProvider(initial)
        repository.upsertProvider(imported)
        repository.upsertProvider(saved)

        val savedProviders = repository.providers.first()
        assertEquals(1, savedProviders.size)
        val persisted = savedProviders.single()
        assertEquals("provider-1", persisted.id)
        assertEquals("OpenAI Primary", persisted.name)
        assertEquals(20_000, persisted.timeout)
        assertEquals(2, persisted.models.size)
        assertTrue(persisted.models.any { it.id == "gpt-4o" })
        assertTrue(persisted.models.any { it.id == "gpt-4.1-mini" })
    }

    private fun createRepository(): SettingsRepository {
        val file = File.createTempFile("settings-repository", ".preferences_pb")
        file.deleteOnExit()
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        return SettingsRepository(dataStore)
    }
}
