package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import org.junit.Assert.assertEquals
import org.junit.Test

class RecorderErrorMessagesTest {
    @Test
    fun permissionDenied_hasItsOwnMessage() {
        assertEquals(
            R.string.recording_error_permission,
            recorderErrorMessageRes(AudioRecorder.ErrorKind.PERMISSION_DENIED),
        )
    }

    @Test
    fun micUnavailable_hasItsOwnMessage() {
        assertEquals(
            R.string.recording_error_mic_unavailable,
            recorderErrorMessageRes(AudioRecorder.ErrorKind.MIC_UNAVAILABLE),
        )
    }

    @Test
    fun otherKindsAndUnknown_fallBackToGenericMessage() {
        assertEquals(R.string.recording_error_failed, recorderErrorMessageRes(AudioRecorder.ErrorKind.CAPTURE_FAILED))
        assertEquals(R.string.recording_error_failed, recorderErrorMessageRes(null))
    }
}
