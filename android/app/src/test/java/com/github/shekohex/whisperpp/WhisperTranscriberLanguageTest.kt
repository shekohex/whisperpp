package com.github.shekohex.whisperpp

import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import okhttp3.MultipartBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WhisperTranscriberLanguageTest {
    @Test
    fun whisperAsr_usesAutoLanguageByDefault() {
        val request = WhisperTranscriber().buildWhisperRequest(
            filename = tempAudioFile().absolutePath,
            mediaType = "audio/wav",
            provider = provider(ProviderType.WHISPER_ASR),
            modelId = "unused",
            prompt = "",
            temperature = 0f,
            languageCode = "auto",
        )

        assertEquals("auto", request.url.queryParameter("language"))
    }

    @Test
    fun whisperAsr_usesSelectedLanguage() {
        val request = WhisperTranscriber().buildWhisperRequest(
            filename = tempAudioFile().absolutePath,
            mediaType = "audio/wav",
            provider = provider(ProviderType.WHISPER_ASR),
            modelId = "unused",
            prompt = "",
            temperature = 0f,
            languageCode = "ar",
        )

        assertEquals("ar", request.url.queryParameter("language"))
    }

    @Test
    fun openAiMultipart_omitsLanguageWhenAuto() {
        val request = WhisperTranscriber().buildWhisperRequest(
            filename = tempAudioFile().absolutePath,
            mediaType = "audio/wav",
            provider = provider(ProviderType.OPENAI),
            modelId = "whisper-1",
            prompt = "",
            temperature = 0f,
            languageCode = "auto",
        )

        val multipart = request.body as MultipartBody
        assertFalse(multipart.hasFormPartNamed("language"))
    }

    @Test
    fun openAiMultipart_includesLanguageWhenNotAuto() {
        val request = WhisperTranscriber().buildWhisperRequest(
            filename = tempAudioFile().absolutePath,
            mediaType = "audio/wav",
            provider = provider(ProviderType.OPENAI),
            modelId = "whisper-1",
            prompt = "",
            temperature = 0f,
            languageCode = "en",
        )

        val multipart = request.body as MultipartBody
        assertTrue(multipart.hasFormPartNamed("language"))
    }

    @Test
    fun customMultipart_includesLanguageWhenNotAuto() {
        val request = WhisperTranscriber().buildWhisperRequest(
            filename = tempAudioFile().absolutePath,
            mediaType = "audio/wav",
            provider = provider(ProviderType.CUSTOM),
            modelId = "whisper-1",
            prompt = "",
            temperature = 0f,
            languageCode = "en",
        )

        val multipart = request.body as MultipartBody
        assertTrue(multipart.hasFormPartNamed("language"))
    }

    private fun provider(type: ProviderType): ServiceProvider {
        return ServiceProvider(
            name = type.name,
            type = type,
            endpoint = "https://example.com/",
            authMode = ProviderAuthMode.NO_AUTH,
            models = emptyList(),
        )
    }

    private fun tempAudioFile(): File {
        val file = File.createTempFile("whisperpp-test-audio", ".wav")
        file.writeBytes(ByteArray(8))
        file.deleteOnExit()
        return file
    }
}

private fun MultipartBody.hasFormPartNamed(name: String): Boolean {
    return parts.any { part ->
        val disposition = part.headers?.get("Content-Disposition") ?: return@any false
        disposition.contains("name=\"$name\"")
    }
}
