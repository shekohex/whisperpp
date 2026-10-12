package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ElevenLabsDialectTest {
    private val factory = FakeWsFactory()

    private fun session(language: String? = null, vocabulary: List<String> = emptyList()) = testSession(
        factory,
        ProviderType.ELEVENLABS,
        { ElevenLabsDialect(streamingConfig("https://api.elevenlabs.io/v1", "scribe_v2", language, vocabulary)) },
    )

    private val started = """{"message_type":"session_started","session_id":"s"}"""

    private fun partial(text: String) = """{"message_type":"partial_transcript","text":"$text"}"""

    private fun committed(text: String, kind: String = "committed_transcript") =
        """{"message_type":"$kind","text":"$text"}"""

    @Test
    fun upgradeRequest_hasParametersAndApiKeyHeader() = runTest {
        session(language = "ar", vocabulary = listOf("Kotlin", "a".repeat(21), "bad<term>", "back\\slash", "ok")).start()

        val request = factory.sockets.single().request()
        assertEquals("/v1/speech-to-text/realtime", request.url.encodedPath)
        val url = request.url
        assertEquals("scribe_v2", url.queryParameter("model_id"))
        assertEquals("pcm_16000", url.queryParameter("audio_format"))
        assertEquals("manual", url.queryParameter("commit_strategy"))
        assertEquals("true", url.queryParameter("no_verbatim"))
        assertEquals("ar", url.queryParameter("language_code"))
        assertEquals(listOf("Kotlin", "ok"), url.queryParameterValues("keyterms"))
        assertEquals("test-key", request.header("xi-api-key"))
        assertNull(request.header("Authorization"))
    }

    @Test
    fun autoLanguage_omitsLanguageCode() = runTest {
        session().start()
        assertNull(factory.sockets.single().request().url.queryParameter("language_code"))
    }

    @Test
    fun keyterms_areCappedAtFifty() {
        assertEquals(50, ElevenLabsDialect.keyterms((1..80).map { "t$it" }).size)
    }

    @Test
    fun audioIsHeldUntilSessionStarted_thenSentAsBase64Json() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        val pcm = byteArrayOf(1, 2, 3, 4)
        session.sendAudio(pcm)
        socket.open()
        session.sendAudio(byteArrayOf(5, 6))

        assertTrue("nothing is sent before session_started", socket.frames.isEmpty())
        assertTrue(events.isEmpty())

        socket.receive(started)
        runCurrent()

        val chunks = socket.json
        assertEquals(2, chunks.size)
        assertEquals("input_audio_chunk", chunks[0].getString("message_type"))
        assertEquals(pcm.toByteString().base64(), chunks[0].getString("audio_base_64"))
        assertFalse(chunks[0].getBoolean("commit"))
        assertEquals(16000, chunks[0].getInt("sample_rate"))
        assertEquals(byteArrayOf(5, 6).toByteString().base64(), chunks[1].getString("audio_base_64"))
        assertEquals(listOf(StreamEvent.Connected), events)
    }

    @Test
    fun commit_sendsEmptyChunkWithCommitTrue_andFirstCommittedIsFinal() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(started)
        session.sendAudio(byteArrayOf(1, 2))
        socket.receive(partial("hello wor"))
        socket.receive(partial("hello world"))

        val result = async { session.commit() }
        runCurrent()
        val commit = socket.json.last()
        assertEquals("", commit.getString("audio_base_64"))
        assertTrue(commit.getBoolean("commit"))
        assertEquals(16000, commit.getInt("sample_rate"))

        socket.receive(committed("Hello world."))
        // A second variant of the same commit must not change the result.
        socket.receive(committed("Hello world, again", kind = "committed_transcript_with_timestamps"))
        runCurrent()

        assertEquals("Hello world.", result.await().getOrThrow().text)
        assertEquals(listOf("hello wor", "hello world"), events.partials())
        assertEquals(1000, socket.closeCode)
    }

    @Test
    fun commitBeforeSessionStarted_isQueuedAfterAudio() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        session.sendAudio(byteArrayOf(1))

        val result = async { session.commit() }
        runCurrent()
        assertTrue(socket.frames.isEmpty())

        socket.receive(started)
        socket.receive(committed("late text"))
        runCurrent()

        assertEquals(listOf(false, true), socket.json.map { it.getBoolean("commit") })
        assertEquals("late text", result.await().getOrThrow().text)
    }

    @Test
    fun committedBeforeCommit_isAPartialNotTheEnd() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(started)

        socket.receive(committed("one"))
        socket.receive(partial("two"))
        runCurrent()

        assertEquals(listOf("one", "one two"), events.partials())
        assertTrue(events.none { it is StreamEvent.Error || it is StreamEvent.Final })
    }

    @Test
    fun errorKinds_mapToTypedErrors() = runTest {
        val expectations = mapOf<String, (Throwable?) -> Boolean>(
            "auth_error" to { it is NetworkError.Unauthorized },
            "quota_exceeded" to { it is NetworkError.RateLimited },
            "rate_limited" to { it is NetworkError.RateLimited },
            "resource_exhausted" to { it is NetworkError.RateLimited },
            "session_time_limit_exceeded" to { it is NetworkError.InvalidResponse },
            "input_error" to { it is NetworkError.InvalidResponse },
            "chunk_size_exceeded" to { it is NetworkError.InvalidResponse },
            "transcriber_error" to { it is NetworkError.InvalidResponse },
            "error" to { it is NetworkError.InvalidResponse },
        )
        for ((kind, check) in expectations) {
            val local = FakeWsFactory()
            val session = testSession(
                local,
                ProviderType.ELEVENLABS,
                { ElevenLabsDialect(streamingConfig("https://api.elevenlabs.io/v1", "scribe_v2")) },
            )
            session.start()
            val socket = local.sockets.single()
            socket.open()

            socket.receive("""{"message_type":"$kind","error":"details with secret transcript"}""")

            val error = session.commit().exceptionOrNull()
            assertTrue(kind, check(error))
            assertFalse("error text never echoes provider messages", error!!.message.orEmpty().contains("secret"))
            assertTrue(socket.cancelled)
        }
    }

    @Test
    fun authErrorBeforeSessionStarted_failsTheStream() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        socket.receive("""{"message_type":"auth_error"}""")
        runCurrent()

        assertTrue(events.any { it is StreamEvent.Error && it.error is NetworkError.Unauthorized })
        assertTrue(events.none { it == StreamEvent.Connected })
    }
}
