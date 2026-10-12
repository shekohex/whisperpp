package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SonioxDialectTest {
    private val factory = FakeWsFactory()

    private fun session(language: String? = null, vocabulary: List<String> = emptyList()) = testSession(
        factory,
        ProviderType.SONIOX,
        { SonioxDialect(streamingConfig("https://stt-rt.soniox.com", "stt-async-v5", language, vocabulary, apiKey = "sx-key")) },
    )

    private fun tokens(vararg tokens: Pair<String, Boolean>, finished: Boolean = false): String {
        val array = tokens.joinToString(",") { (text, final) -> """{"text":${JSONObject.quote(text)},"is_final":$final}""" }
        return """{"tokens":[$array],"final_audio_proc_ms":0${if (finished) ""","finished":true""" else ""}}"""
    }

    @Test
    fun upgradeRequest_hasNoAuthHeader_andConfigFrameCarriesTheKey() = runTest {
        val session = session(language = "ar", vocabulary = listOf("Kotlin", "Whisper"))
        session.start()
        val socket = factory.sockets.single()

        assertEquals("/transcribe-websocket", socket.request().url.encodedPath)
        assertNull(socket.request().header("Authorization"))
        assertTrue(socket.request().url.toString().contains("sx-key").not())

        socket.open()
        val config = socket.json.single()
        assertEquals("sx-key", config.getString("api_key"))
        assertEquals("stt-rt-v5", config.getString("model"))
        assertEquals("pcm_s16le", config.getString("audio_format"))
        assertEquals(16000, config.getInt("sample_rate"))
        assertEquals(1, config.getInt("num_channels"))
        assertTrue(config.getBoolean("enable_language_identification"))
        assertEquals("ar", config.getJSONArray("language_hints").getString(0))
        assertTrue(config.getBoolean("language_hints_strict"))
        assertEquals("Kotlin", config.getJSONObject("context").getJSONArray("terms").getString(0))
    }

    @Test
    fun autoLanguageAndNoVocabulary_omitHintsAndContext() = runTest {
        session().start()
        val socket = factory.sockets.single()
        socket.open()

        val config = socket.json.single()
        assertFalse(config.has("language_hints"))
        assertFalse(config.has("language_hints_strict"))
        assertFalse(config.has("context"))
    }

    @Test
    fun audioIsBinary_andBufferedBehindTheConfigFrame() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        session.sendAudio(byteArrayOf(1, 2))
        socket.open()
        session.sendAudio(byteArrayOf(3))

        assertTrue(socket.frames[0] is String)
        assertEquals(listOf(listOf<Byte>(1, 2), listOf<Byte>(3)), socket.binaries.map { it.toList() })
    }

    @Test
    fun tokens_buildCumulativePartials_andFinishedIsTheFinal() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        socket.receive(tokens("Hel" to false))
        socket.receive(tokens("Hello" to true, " wor" to false))
        socket.receive(tokens(" world" to true, "<fin>" to true))
        socket.receive(tokens("Next" to false))
        runCurrent()
        assertEquals(listOf("Hel", "Hello wor", "Hello world", "Hello world Next"), events.partials())

        val result = async { session.commit() }
        runCurrent()
        assertEquals("""{"type":"finalize"}""", socket.texts[1])
        assertEquals("the empty frame ends the audio", "", socket.texts.last())
        socket.receive(tokens(" more" to true, "<fin>" to true, finished = true))
        runCurrent()

        assertEquals("Hello world more", result.await().getOrThrow().text)
        assertEquals(1000, socket.closeCode)
        assertEquals(StreamEvent.Closed, events.last())
    }

    @Test
    fun finishedWithoutFin_commitsTheRemainingBuffer() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(tokens("tail" to true))

        val result = async { session.commit() }
        runCurrent()
        socket.receive("""{"tokens":[],"finished":true}""")
        runCurrent()

        assertEquals("tail", result.await().getOrThrow().text)
    }

    @Test
    fun errorMessage_failsTheStream() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        socket.receive("""{"error_code":401,"error_message":"Invalid API key"}""")
        runCurrent()

        assertTrue(events.any { it is StreamEvent.Error && it.error is NetworkError.Unauthorized })
        assertTrue(session.commit().exceptionOrNull() is NetworkError.Unauthorized)
        assertTrue(socket.cancelled)
    }

    @Test
    fun wireModel_mapsAsyncIdToRealtime() {
        assertEquals("stt-rt-v5", SonioxDialect.wireModel("stt-async-v5"))
        assertEquals("stt-rt-v5", SonioxDialect.wireModel("stt-rt-v5"))
    }
}
