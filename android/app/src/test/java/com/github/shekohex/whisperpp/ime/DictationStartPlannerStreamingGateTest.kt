package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.domain.providers.Transcript
import com.github.shekohex.whisperpp.domain.providers.TranscriptionProvider
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DictationStartPlannerStreamingGateTest {
    private class FakeProvider(
        realtime: Boolean,
        override val streamingOnly: Boolean = false,
    ) : TranscriptionProvider {
        override val id = "p"
        override val capabilities = ModelCapabilities(stt = true, realtime = realtime)

        override suspend fun transcribe(audio: File, req: TranscriptionRequest): Result<Transcript> =
            Result.failure(UnsupportedOperationException())

        override suspend fun verify(): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun realtimeModels_streamWhenTheSettingIsOnOrUnset() {
        val provider = FakeProvider(realtime = true)
        assertTrue(DictationStartPlanner.shouldStream(provider, null))
        assertTrue(DictationStartPlanner.shouldStream(provider, true))
        assertFalse(DictationStartPlanner.shouldStream(provider, false))
    }

    @Test
    fun streamingOnlyProviders_ignoreTheSetting() {
        val provider = FakeProvider(realtime = true, streamingOnly = true)
        assertTrue(DictationStartPlanner.shouldStream(provider, false))
    }

    @Test
    fun nonRealtimeModels_neverStream() {
        assertFalse(DictationStartPlanner.shouldStream(FakeProvider(realtime = false), true))
        assertFalse(DictationStartPlanner.shouldStream(FakeProvider(realtime = false, streamingOnly = true), true))
    }
}
