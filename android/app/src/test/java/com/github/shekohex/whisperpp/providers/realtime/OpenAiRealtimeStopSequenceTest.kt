package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The deterministic stop sequence: VAD off, commit or clear, clear as barrier, then every item. */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenAiRealtimeStopSequenceTest {
    private val factory = FakeWsFactory()

    private suspend fun TestScope.readySession(
        vad: Vad = Vad.ServerVad(),
        schema: RealtimeSchema = RealtimeSchema.GA,
    ): Pair<OpenAiRealtimeSession, FakeWsSocket> {
        val session = openAiSession(factory, vad = vad, schema = schema)
        session.start()
        val socket = factory.sockets.last()
        socket.open()
        socket.receive(if (schema == RealtimeSchema.GA) gaSessionUpdated(vad is Vad.ServerVad) else betaSessionUpdated(vad is Vad.ServerVad))
        runCurrent()
        return session to socket
    }

    @Test
    fun silence_disablesVad_thenClears_andNeverCommits() = runTest {
        val (session, socket) = readySession()
        session.sendAudio(ByteArray(OPENAI_SPEECH_BYTES))

        val result = async { session.commit() }
        runCurrent()

        // Step 1: only the VAD-off update goes out; nothing else until it is confirmed.
        assertEquals(listOf("session.update", "input_audio_buffer.append", "session.update"), socket.types())
        val disable = socket.json.last().getJSONObject("session")
        assertEquals("transcription", disable.getString("type"))
        assertTrue(disable.getJSONObject("audio").getJSONObject("input").isNull("turn_detection"))
        assertFalse(result.isCompleted)

        // Step 2: no speech was heard, so the buffer is cleared, not committed.
        socket.receive(gaSessionUpdated(vadOn = false))
        runCurrent()
        assertEquals("input_audio_buffer.clear", socket.types().last())
        assertFalse(socket.types().contains("input_audio_buffer.commit"))
        assertFalse(result.isCompleted)

        socket.receive(CLEARED_EVENT)
        runCurrent()
        assertEquals("", result.await().getOrThrow().text)
    }

    @Test
    fun pendingSpeech_isCommitted_thenCleared_thenWaitsForItsTranscript() = runTest {
        val (session, socket) = readySession()
        socket.receive(speechStartedEvent("item_1"))
        session.sendAudio(ByteArray(OPENAI_SPEECH_BYTES))

        val result = async { session.commit() }
        runCurrent()
        socket.receive(gaSessionUpdated(vadOn = false))
        runCurrent()
        assertEquals("input_audio_buffer.commit", socket.types().last())

        socket.receive(committedEvent("item_1"))
        runCurrent()
        assertEquals("input_audio_buffer.clear", socket.types().last())

        socket.receive(CLEARED_EVENT)
        runCurrent()
        assertFalse("the item has no transcript yet", result.isCompleted)

        socket.receive(deltaEvent("item_1", "Still"))
        socket.receive(completedEvent("item_1", "Still talking"))
        runCurrent()
        assertEquals("Still talking", result.await().getOrThrow().text)
    }

    @Test
    fun stopSequence_orderOnTheWire() = runTest {
        val (session, socket) = readySession()
        socket.utterance("item_1", "Done.")
        socket.receive(speechStartedEvent("item_2"))
        session.sendAudio(ByteArray(OPENAI_SPEECH_BYTES))

        val result = async { session.commit() }
        runCurrent()
        socket.receive(gaSessionUpdated(vadOn = false))
        socket.receive(committedEvent("item_2"))
        socket.receive(CLEARED_EVENT)
        socket.receive(completedEvent("item_2", "More."))
        runCurrent()

        assertEquals(
            listOf("session.update", "input_audio_buffer.append", "session.update", "input_audio_buffer.commit", "input_audio_buffer.clear"),
            socket.types(),
        )
        assertEquals("Done. More.", result.await().getOrThrow().text)
    }

    @Test
    fun staleSessionUpdated_withVadStillOn_doesNotAdvanceTheSequence() = runTest {
        val (session, socket) = readySession()
        val result = async { session.commit() }
        runCurrent()

        socket.receive(gaSessionUpdated(vadOn = true))
        runCurrent()
        assertEquals(listOf("session.update", "session.update"), socket.types())

        socket.receive(gaSessionUpdated(vadOn = false))
        runCurrent()
        assertEquals("input_audio_buffer.clear", socket.types().last())
        socket.receive(CLEARED_EVENT)
        runCurrent()
        assertTrue(result.await().isSuccess)
    }

    @Test
    fun clearedAlone_isNotEnough_untilEveryItemCompleted() = runTest {
        val (session, socket) = readySession()
        socket.receive(speechStartedEvent("item_1"))
        socket.receive(speechStoppedEvent("item_1"))
        socket.receive(committedEvent("item_1"))
        val result = async { session.commit() }
        runCurrent()
        socket.receive(gaSessionUpdated(vadOn = false))
        socket.receive(CLEARED_EVENT)
        runCurrent()
        assertFalse(result.isCompleted)

        socket.receive(completedEvent("item_1", "Hi"))
        runCurrent()
        assertEquals("Hi", result.await().getOrThrow().text)
    }

    @Test
    fun speechThatNeverCommits_doesNotFinish_andTimesOutForBatchFallback() = runTest {
        val (session, socket) = readySession()
        // Heard by the server, but its commit never arrives and ours is not acknowledged.
        socket.receive(speechStartedEvent("item_1"))
        val result = async { session.commit() }
        runCurrent()
        socket.receive(gaSessionUpdated(vadOn = false))
        runCurrent()
        assertEquals("input_audio_buffer.commit", socket.types().last())

        testScheduler.advanceTimeBy(OPENAI_TEST_COMMIT_TIMEOUT_MS + 1)
        runCurrent()
        assertTrue(result.await().exceptionOrNull() is NetworkError.Timeout)
    }

    @Test
    fun emptyCommitError_meansTheVadAlreadyCommitted_soTheSequenceCarriesOn() = runTest {
        val (session, socket) = readySession()
        socket.utterance("item_1", "Only this.")
        socket.receive(speechStartedEvent("item_2"))
        val result = async { session.commit() }
        runCurrent()
        socket.receive(gaSessionUpdated(vadOn = false))
        runCurrent()
        assertEquals("input_audio_buffer.commit", socket.types().last())

        socket.receive(EMPTY_COMMIT_ERROR)
        runCurrent()
        assertEquals("input_audio_buffer.clear", socket.types().last())
        socket.receive(CLEARED_EVENT)
        runCurrent()
        // item_2 never got a buffer; it is not waited for.
        assertEquals("Only this.", result.await().getOrThrow().text)
    }

    @Test
    fun vadAutoCommitRacingOurCommit_isTolerated() = runTest {
        val (session, socket) = readySession()
        socket.receive(speechStartedEvent("item_1"))
        val result = async { session.commit() }
        runCurrent()
        socket.receive(gaSessionUpdated(vadOn = false))
        // The VAD's own commit lands first and ours is rejected as empty.
        socket.receive(committedEvent("item_1"))
        socket.receive(EMPTY_COMMIT_ERROR)
        socket.receive(CLEARED_EVENT)
        socket.receive(completedEvent("item_1", "Race."))
        runCurrent()

        assertEquals("Race.", result.await().getOrThrow().text)
        assertEquals(1, socket.types().count { it == "input_audio_buffer.clear" })
    }

    @Test
    fun otherServerErrorsDuringStop_failTheStream() = runTest {
        val (session, socket) = readySession()
        val result = async { session.commit() }
        runCurrent()

        socket.receive("""{"type":"error","error":{"type":"server_error","code":"boom","message":"x"}}""")
        runCurrent()

        assertTrue(result.await().exceptionOrNull() is NetworkError.InvalidResponse)
        assertTrue(socket.cancelled)
    }

    @Test
    fun withoutVad_audioIsCommittedDirectly_thenCleared() = runTest {
        val (session, socket) = readySession(vad = Vad.None)
        session.sendAudio(ByteArray(OPENAI_SPEECH_BYTES))

        val result = async { session.commit() }
        runCurrent()
        assertEquals(listOf("session.update", "input_audio_buffer.append", "input_audio_buffer.commit"), socket.types())
        socket.receive(committedEvent("item_1"))
        runCurrent()
        assertEquals("input_audio_buffer.clear", socket.types().last())
        socket.receive(CLEARED_EVENT)
        socket.receive(completedEvent("item_1", "ok"))
        runCurrent()

        assertEquals("ok", result.await().getOrThrow().text)
    }

    @Test
    fun commitBeforeSessionUpdated_runsTheSequenceAfterTheBufferedAudioIsFlushed() = runTest {
        val session = openAiSession(factory)
        session.start()
        session.sendAudio(byteArrayOf(1, 2))
        val result = async { session.commit() }
        runCurrent()
        val socket = factory.sockets.single()
        socket.open()
        assertEquals(listOf("session.update"), socket.types())

        socket.receive(gaSessionUpdated())
        runCurrent()
        assertEquals(listOf("session.update", "input_audio_buffer.append", "session.update"), socket.types())

        socket.receive(gaSessionUpdated(vadOn = false))
        socket.receive(CLEARED_EVENT)
        runCurrent()
        assertEquals("", result.await().getOrThrow().text)
    }

    @Test
    fun commitFlushesTheResamplerTail() = runTest {
        val session = openAiSession(factory, inputSampleRate = 16_000)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(gaSessionUpdated())
        session.sendAudio(ByteArray(640))
        val before = socket.json.drop(1).count { it.getString("type") == "input_audio_buffer.append" }

        val result = async { session.commit() }
        runCurrent()

        val appends = socket.json.drop(1).count { it.getString("type") == "input_audio_buffer.append" }
        assertEquals(before + 1, appends)
        assertEquals("session.update", socket.types().last())
        session.cancel()
        result.await()
    }

    @Test
    fun afterCommit_audioIsIgnored() = runTest {
        val (session, socket) = readySession()
        val result = async { session.commit() }
        runCurrent()
        val sent = socket.frames.size

        session.sendAudio(ByteArray(OPENAI_SPEECH_BYTES))

        assertEquals(sent, socket.frames.size)
        session.cancel()
        result.await()
    }
}
