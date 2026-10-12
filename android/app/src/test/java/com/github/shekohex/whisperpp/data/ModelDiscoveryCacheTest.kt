package com.github.shekohex.whisperpp.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.domain.providers.DiscoveredCache
import com.github.shekohex.whisperpp.domain.providers.DiscoveredModel
import com.github.shekohex.whisperpp.domain.providers.DiscoveryIdentity
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelDiscoveryCacheTest {

    @Test
    fun saveDiscovered_roundTripsPerProvider_andKeepsProviderModels() = runBlocking {
        val (repository, _) = createRepository()
        val provider = ServiceProvider(
            id = "p1",
            name = "OpenAI",
            type = ProviderType.OPENAI,
            endpoint = "https://api.openai.com/v1",
            models = listOf(ModelConfig("manual-model", "Manual")),
        )
        repository.upsertProvider(provider)
        val whisper = DiscoveredModel(
            id = "whisper-1",
            displayName = "whisper-1",
            capabilities = ModelCapabilities.stt(ProviderType.OPENAI),
            raw = mapOf("owned_by" to "openai"),
        )
        val gpt = DiscoveredModel("gpt-4o", "GPT-4o", ModelCapabilities.TEXT)

        repository.saveDiscovered("p1", listOf(whisper, gpt), fetchedAtMillis = 1_000L)
        repository.saveDiscovered("p2", listOf(gpt), fetchedAtMillis = 2_000L)

        assertEquals(
            DiscoveredCache(1_000L, listOf(whisper, gpt), DiscoveryIdentity.of(provider)),
            repository.discoveredModels("p1").first(),
        )
        assertEquals(DiscoveredCache(2_000L, listOf(gpt)), repository.discoveredModels("p2").first())
        assertNull(repository.discoveredModels("missing").first())
        assertEquals(provider, repository.providers.first().single())

        repository.saveDiscovered("p1", listOf(gpt), fetchedAtMillis = 3_000L)
        val refreshed = repository.discoveredModels("p1").first()
        assertEquals(3_000L, refreshed?.fetchedAtMillis)
        assertTrue(refreshed?.contains("manual-model") == false)
        assertEquals(listOf(ModelConfig("manual-model", "Manual")), repository.providers.first().single().models)
    }

    @Test
    fun cacheIsIgnored_whenEndpointOrTypeChanged() = runBlocking {
        val (repository, _) = createRepository()
        val provider = ServiceProvider(id = "p1", name = "P", type = ProviderType.OPENAI, endpoint = "https://api.openai.com/v1", models = emptyList())
        repository.upsertProvider(provider)
        val gpt = DiscoveredModel("gpt-4o", "GPT-4o", ModelCapabilities.TEXT)
        repository.saveDiscovered("p1", listOf(gpt), fetchedAtMillis = 1L)

        // Same endpoint modulo trailing slash / host case is the same listing.
        repository.upsertProvider(provider.copy(endpoint = "https://API.openai.com/v1/"))
        assertEquals(listOf(gpt), repository.discoveredModels("p1").first()?.models)

        repository.upsertProvider(provider.copy(endpoint = "https://api.groq.com/openai/v1"))
        assertNull(repository.discoveredModels("p1").first())

        repository.upsertProvider(provider.copy(type = ProviderType.CUSTOM))
        assertNull(repository.discoveredModels("p1").first())

        // An explicit identity (e.g. an unsaved editor draft) wins over the saved provider.
        repository.upsertProvider(provider)
        val draft = DiscoveryIdentity.of(provider.copy(endpoint = "http://localhost:8000/v1"))
        assertNull(repository.discoveredModels("p1", draft).first())
        repository.saveDiscovered("p1", listOf(gpt), fetchedAtMillis = 2L, identity = draft)
        assertEquals(2L, repository.discoveredModels("p1", draft).first()?.fetchedAtMillis)
        assertNull(repository.discoveredModels("p1").first())
    }

    @Test
    fun corruptOrPartialCache_isSanitized() = runBlocking {
        val (repository, dataStore) = createRepository()
        dataStore.edit { it[PreferenceKeys.MODEL_DISCOVERY_CACHE] = "not json" }
        assertNull(repository.discoveredModels("p1").first())

        dataStore.edit {
            it[PreferenceKeys.MODEL_DISCOVERY_CACHE] = """{"p1":{"fetchedAtMillis":5,"models":[{"id":"gpt-4o"},{"displayName":"no id"}]}}"""
        }
        val cache = repository.discoveredModels("p1").first()

        assertEquals(DiscoveredCache(5, listOf(DiscoveredModel("gpt-4o", "gpt-4o", ModelCapabilities()))), cache)
        assertEquals(emptyMap<String, DiscoveredCache>(), parseDiscoveryCacheJson(null, Gson()))
    }

    private fun createRepository() = run {
        val file = File.createTempFile("discovery-cache", ".preferences_pb")
        file.delete()
        file.deleteOnExit()
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        SettingsRepository(dataStore) to dataStore
    }
}
