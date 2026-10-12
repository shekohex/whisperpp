package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.data.history.HistoryAudioStore
import java.io.File

/**
 * Where a dictation take is recorded. With history on, straight into the durable recordings store
 * so the audio is on disk from its first sample and survives a failure or a killed process; with
 * history off, a throwaway file in the cache that is deleted when the run ends.
 */
class DictationRecordingFiles(
    private val historyEnabled: suspend () -> Boolean,
    private val durable: HistoryAudioStore,
    private val temporary: RecordingFiles,
) {
    suspend fun newFile(): File = if (historyEnabled()) durable.newRecordingFile() else temporary.newFile()
}
