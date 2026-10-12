package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.core.audio.AudioRecorder

internal fun AudioRecorder.ErrorKind?.toSessionErrorKind(): SessionState.ErrorKind = when (this) {
    AudioRecorder.ErrorKind.PERMISSION_DENIED -> SessionState.ErrorKind.PERMISSION_DENIED
    AudioRecorder.ErrorKind.MIC_UNAVAILABLE -> SessionState.ErrorKind.MIC_UNAVAILABLE
    else -> SessionState.ErrorKind.RECORDING_FAILED
}
