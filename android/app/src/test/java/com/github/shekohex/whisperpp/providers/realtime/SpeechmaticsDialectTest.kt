package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechmaticsDialectTest {
    private val factory = FakeWsFactory()

    private fun session(
        language: String? = null,
        vocabulary: List<String> = emptyList(),
        model: String = "speechmatics-enhanced",
    ) = testSession(
        factory,
        ProviderType.SPEECHMATICS,
        { SpeechmaticsDialect(streamingConfig("https://eu2.rt.speechmatics.com", model, language, vocabulary)) },
    )

    private val started = """{"message":"RecognitionStarted","id":"x"}"""

    private fun partial(text: String) = """{"message":"AddPartialTranscript","metadata":{"transcript":"$text"}}"""

    private fun final(text: String) = """{"message":"AddTranscript","metadata":{"transcript":"$text"}}"""

    @Test
    fun upgradeRequest_usesBearerAuth_andV2Path() = runTest {
        session().start()

        val request = factory.sockets.single().request()
        assertEquals("/v2", request.url.encodedPath)
        assertEquals("eu2.rt.speechmatics.com", request.url.host)
        assertEquals("Bearer test-key", request.header("Authorization"))
    }

    @Test
    fun startRecognition_isSentOnOpen_withLanguageMapping_andVocabulary() = runTest {
        session(language = "zh-CN", vocabulary = listOf("Kotlin")).start()
        val socket = factory.sockets.single()
        socket.open()

        val start = socket.json.single()
        assertEquals("StartRecognition", start.getString("message"))
        val format = start.getJSONObject("audio_format")
        assertEquals("raw", format.getString("type"))
        assertEquals("pcm_s16le", format.getString("encoding"))
        assertEquals(16000, format.getInt("sample_rate"))
        val config = start.getJSONObject("transcription_config")
        assertEquals("cmn", config.getString("language"))
        assertTrue(config.getBoolean("enable_partials"))
        assertEquals("enhanced", config.getString("operating_point"))
        assertEquals(2.0, config.getDouble("max_delay"), 0.0)
        assertEquals("flexible", config.getString("max_delay_mode"))
        assertEquals("Kotlin", config.getJSONArray("additional_vocab").getJSONObject(0).getString("content"))
    }

    @Test
    fun autoLanguageDefaultsToEnglish_andVocabularyIsOmittedWhenEmpty() = runTest {
        session().start()
        val socket = factory.sockets.single()
        socket.open()

        val config = socket.json.single().getJSONObject("transcription_config")
        assertEquals("en", config.getString("language"))
        assertFalse(config.has("additional_vocab"))
        assertEquals("ar", SpeechmaticsDialect.languageFor("ar"))
        assertEquals("standard", SpeechmaticsDialect.operatingPoint("speechmatics-standard"))
    }

    @Test
    fun audioWaitsForRecognitionStarted_andEndOfStreamCountsChunks() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        session.sendAudio(byteArrayOf(1))
        socket.open()
        session.sendAudio(byteArrayOf(2))
        assertEquals("only StartRecognition so far", 1, socket.frames.size)

        socket.receive(started)
        session.sendAudio(byteArrayOf(3))
        runCurrent()
        assertEquals(listOf(listOf<Byte>(1), listOf<Byte>(2), listOf<Byte>(3)), socket.binaries.map { it.toList() })
        assertEquals(listOf(StreamEvent.Connected), events)

        val result = async { session.commit() }
        runCurrent()
        val end = socket.json.last()
        assertEquals("EndOfStream", end.getString("message"))
        assertEquals(3, end.getInt("last_seq_no"))

        socket.receive("""{"message":"EndOfTranscript"}""")
        runCurrent()
        result.await()
    }

    @Test
    fun transcripts_accumulate_andPunctuationIsCleaned() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(started)

        socket.receive(partial("Hel"))
        socket.receive(final("Hello"))
        socket.receive(final(","))
        socket.receive(partial("wor"))
        socket.receive(final("world"))
        socket.receive(final("!"))
        runCurrent()
        assertEquals(listOf("Hel", "Hello", "Hello,", "Hello, wor", "Hello, world", "Hello, world!"), events.partials())

        val result = async { session.commit() }
        runCurrent()
        socket.receive("""{"message":"EndOfTranscript"}""")
        runCurrent()

        assertEquals("Hello, world!", result.await().getOrThrow().text)
        assertEquals(1000, socket.closeCode)
    }

    @Test
    fun errorMessage_failsTheStream() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        socket.receive("""{"message":"Error","type":"not_authorised","reason":"Invalid key"}""")
        runCurrent()

        assertTrue(events.any { it is StreamEvent.Error && it.error is NetworkError.Unauthorized })
        assertTrue(session.commit().exceptionOrNull() is NetworkError.Unauthorized)
    }

    @Test
    fun commitWithoutEndOfTranscript_timesOut() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(started)

        val result = async { session.commit() }
        advanceTimeBy(TEST_COMMIT_TIMEOUT_MS + 1)
        runCurrent()

        assertTrue(result.await().exceptionOrNull() is NetworkError.Timeout)
        assertTrue(socket.cancelled)
    }
}
