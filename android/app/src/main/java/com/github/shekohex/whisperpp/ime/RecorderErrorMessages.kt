package com.github.shekohex.whisperpp.ime

import androidx.annotation.StringRes
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.audio.AudioRecorder

/** User-facing message for a recorder failure; a null [kind] means an unclassified failure. */
@StringRes
internal fun recorderErrorMessageRes(kind: AudioRecorder.ErrorKind?): Int = when (kind) {
    AudioRecorder.ErrorKind.PERMISSION_DENIED -> R.string.recording_error_permission
    AudioRecorder.ErrorKind.MIC_UNAVAILABLE -> R.string.recording_error_mic_unavailable
    else -> R.string.recording_error_failed
}
