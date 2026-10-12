package com.github.shekohex.whisperpp.ime

import android.os.SystemClock
import java.io.File

/** Where takes are recorded: one distinct file per take, and cleanup of takes a killed process left behind. */
class RecordingFiles(
    private val directory: () -> File,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private var counter = 0

    /** A distinct file per take, so the late teardown of a previous capture can never delete this one. */
    fun newFile(): File = File(directory(), "$PREFIX${clock()}_${++counter}.wav")

    /** Takes left behind by a killed process; nothing records yet when this runs. */
    fun deleteStale() {
        directory().listFiles { file -> file.name.startsWith(PREFIX) || file.name == LEGACY_FILE_NAME }
            ?.forEach { it.delete() }
    }

    private companion object {
        const val PREFIX = "rec_"
        const val LEGACY_FILE_NAME = "recorded.wav"
    }
}
