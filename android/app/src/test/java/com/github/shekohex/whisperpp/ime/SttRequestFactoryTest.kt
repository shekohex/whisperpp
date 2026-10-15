package com.github.shekohex.whisperpp.ime

import androidx.datastore.preferences.core.emptyPreferences
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.ScriptConversion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SttRequestFactoryTest {

    private val factory = SttRequestFactory(
        scriptConversion = { ScriptConversion.NONE },
        audioMediaType = "audio/wav",
        vocabulary = { emptyList() },
    )

    private val provider = ServiceProvider(
        id = "p1",
        name = "OpenAI",
        type = ProviderType.OPENAI,
        endpoint = "https://api.example.com/v1",
        timeout = 10_000,
        models = listOf(
            ModelConfig(id = "whisper-1", name = "Whisper 1", capabilities = ModelCapabilities.stt(ProviderType.OPENAI)),
            ModelConfig(
                id = "whisper-long",
                name = "Whisper Long",
                capabilities = ModelCapabilities.stt(ProviderType.OPENAI),
                timeoutMs = 45_000,
            ),
        ),
    )

    @Test
    fun effectiveTimeoutMs_modelOverrideWins() {
        assertEquals(45_000L, provider.effectiveTimeoutMs(provider.models[1]))
    }

    @Test
    fun effectiveTimeoutMs_fallsBackToProviderTimeout() {
        assertEquals(10_000L, provider.effectiveTimeoutMs(provider.models[0]))
        assertEquals(10_000L, provider.effectiveTimeoutMs(null))
    }

    @Test
    fun create_usesModelOverride_andFallsBackToProviderTimeout() = runBlocking {
        val overridden = factory.create(emptyPreferences(), provider, "whisper-long", "en")
        assertEquals(45_000L, overridden.timeoutMs)

        val inherited = factory.create(emptyPreferences(), provider, "whisper-1", "en")
        assertEquals(10_000L, inherited.timeoutMs)
    }
}
