package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssemblyAiDialectTest {
    private val factory = FakeWsFactory()

    private fun session(language: String? = null, vocabulary: List<String> = emptyList()) = testSession(
        factory,
        ProviderType.ASSEMBLYAI,
        { AssemblyAiDialect(streamingConfig("https://streaming.assemblyai.com", "universal-3-6-pro", language, vocabulary)) },
    )

    private val begin = """{"type":"Begin","id":"s","expires_at":1}"""

    private fun turn(order: Int, text: String, end: Boolean) =
        """{"type":"Turn","turn_order":$order,"transcript":"$text","end_of_turn":$end}"""

    @Test
    fun upgradeRequest_hasParameters_andBareKeyAuth() = runTest {
        session(language = "en", vocabulary = listOf("Kotlin", "Whisper")).start()

        val request = factory.sockets.single().request()
        assertEquals("/v3/ws", request.url.encodedPath)
        val url = request.url
        assertEquals("16000", url.queryParameter("sample_rate"))
        assertEquals("pcm_s16le", url.queryParameter("encoding"))
        assertEquals("universal-3-6-pro", url.queryParameter("speech_model"))
        assertEquals("balanced", url.queryParameter("mode"))
        assertEquals("""["en"]""", url.queryParameter("language_codes"))
        assertEquals("""["Kotlin","Whisper"]""", url.queryParameter("keyterms_prompt"))
        assertEquals("test-key", request.header("Authorization"))
    }

    @Test
    fun autoLanguage_andNoVocabulary_omitTheOptionalParameters() = runTest {
        session().start()

        val url = factory.sockets.single().request().url
        assertNull(url.queryParameter("language_codes"))
        assertNull(url.queryParameter("keyterms_prompt"))
    }

    @Test
    fun audioIsRechunkedIntoExact1600ByteFrames_afterBegin() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        session.sendAudio(ByteArray(1000) { 1 })
        session.sendAudio(ByteArray(1000) { 2 })
        session.sendAudio(ByteArray(3000) { 3 })
        assertTrue("held until Begin", socket.frames.isEmpty())

        socket.receive(begin)
        runCurrent()

        // 5000 bytes -> three whole frames, 200 bytes pending.
        val frames = socket.binaries
        assertEquals(listOf(1600, 1600, 1600), frames.map { it.size })
        assertEquals(1.toByte(), frames[0][0])
        assertEquals(2.toByte(), frames[0][1599])
        assertEquals(3.toByte(), frames[2][1599])
    }

    @Test
    fun commit_padsTheTailWithZeros_thenTerminates() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(begin)
        session.sendAudio(ByteArray(1700) { 7 })

        val result = async { session.commit() }
        runCurrent()

        val frames = socket.binaries
        assertEquals(listOf(1600, 1600), frames.map { it.size })
        assertEquals(7.toByte(), frames[1][99])
        assertEquals(0.toByte(), frames[1][100])
        assertEquals(0.toByte(), frames[1][1599])
        assertEquals("Terminate", socket.json.last().getString("type"))

        socket.receive("""{"type":"Termination","audio_duration_seconds":1}""")
        runCurrent()
        result.await()
    }

    @Test
    fun commit_withExactFrames_sendsNoPaddingFrame() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(begin)
        session.sendAudio(ByteArray(1600))

        val result = async { session.commit() }
        runCurrent()

        assertEquals(1, socket.binaries.size)
        socket.receive("""{"type":"Termination"}""")
        runCurrent()
        result.await()
    }

    @Test
    fun turns_areOrdered_partialsAreCumulative_andTerminationReturnsTheJoin() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(begin)

        socket.receive(turn(0, "hello", end = false))
        socket.receive(turn(0, "hello there", end = true))
        socket.receive(turn(1, "general", end = false))
        socket.receive(turn(1, "General Kenobi.", end = true))
        // The formatted re-send of turn 0 replaces the earlier text.
        socket.receive(turn(0, "Hello there.", end = true))
        runCurrent()
        assertEquals(
            listOf("hello", "hello there", "hello there general", "hello there General Kenobi.", "Hello there. General Kenobi."),
            events.partials(),
        )

        val result = async { session.commit() }
        runCurrent()
        socket.receive("""{"type":"Termination"}""")
        runCurrent()

        assertEquals("Hello there. General Kenobi.", result.await().getOrThrow().text)
        assertEquals(1000, socket.closeCode)
    }

    @Test
    fun errorMessage_failsTheStream() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        socket.receive("""{"type":"Error","error_code":"unauthorized"}""")
        runCurrent()

        assertTrue(events.any { it is StreamEvent.Error && it.error is NetworkError.Unauthorized })
        assertTrue(socket.cancelled)
    }

    @Test
    fun rechunker_keepsOrderAcrossPushes() {
        val chunker = Rechunker(4)
        assertEquals(emptyList<List<Byte>>(), chunker.push(byteArrayOf(1, 2, 3)).map { it.toList() })
        assertEquals(listOf(listOf<Byte>(1, 2, 3, 4), listOf<Byte>(5, 6, 7, 8)), chunker.push(byteArrayOf(4, 5, 6, 7, 8, 9)).map { it.toList() })
        assertEquals(listOf<Byte>(9, 0, 0, 0), chunker.flushPadded()!!.toList())
        assertNull(chunker.flush())
    }
}
