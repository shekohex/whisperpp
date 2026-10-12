package com.github.shekohex.whisperpp.data.history

import java.io.File
import java.io.IOException

/**
 * Kept recordings under `filesDir/history/` (excluded from Android backups). Every path handed out
 * or deleted is checked to live inside [dir], so a tampered row can never delete other files.
 */
class HistoryAudioStore(private val dir: File) {
    /** Moves (or copies when moving fails) [source] into the store; null on any I/O failure. */
    fun retain(source: File, name: String): String? {
        return try {
            if (!source.isFile) return null
            if (!dir.isDirectory && !dir.mkdirs()) return null
            val target = File(dir, name)
            if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = true)
            }
            target.absolutePath
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    fun delete(path: String) {
        val file = File(path)
        if (isInside(file)) file.delete()
    }

    fun deleteAll(paths: Collection<String>) = paths.forEach(::delete)

    /**
     * Removes files no row points at (e.g. a run whose entry was never written). Files modified after
     * [modifiedBefore] are left alone: their entry may still be on its way.
     */
    fun deleteOrphans(referenced: Set<String>, modifiedBefore: Long) {
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath !in referenced && file.lastModified() < modifiedBefore) file.delete()
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { if (it.isFile) it.delete() }
    }

    private fun isInside(file: File): Boolean {
        return try {
            file.canonicalFile.parentFile == dir.canonicalFile
        } catch (_: IOException) {
            false
        }
    }

    companion object {
        const val DIR_NAME = "history"
    }
}
