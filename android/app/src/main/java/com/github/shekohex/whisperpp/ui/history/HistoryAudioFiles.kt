package com.github.shekohex.whisperpp.ui.history

import java.io.File
import java.io.IOException

/**
 * The kept recording at [path] if it is a file directly inside [audioDir]; a tampered row can't make
 * us open anything else.
 */
fun resolveHistoryAudioFile(audioDir: File, path: String?): File? {
    if (path.isNullOrBlank()) return null
    return try {
        val file = File(path).canonicalFile
        file.takeIf { it.isFile && it.parentFile == audioDir.canonicalFile }
    } catch (_: IOException) {
        null
    }
}
