package com.github.shekohex.whisperpp.data.history

import com.github.shekohex.whisperpp.core.audio.RiffWaveHelper
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Durable recordings under `filesDir/history/` (excluded from Android backups). Takes are recorded
 * straight into [dir], so a take is on disk from its first sample and survives a killed process.
 * Every path handed out or deleted is checked to live inside [dir], so a tampered row can never
 * delete other files. Files of a take that is still being recorded are never treated as orphans.
 */
class HistoryAudioStore(
    private val dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val counter = AtomicInteger(0)

    /** Takes being recorded or not yet referenced by a row; cleanup leaves them alone. */
    private val active = ConcurrentHashMap<String, Long>()

    /** A distinct file per take inside the store, marked in use until [release]. */
    fun newRecordingFile(): File {
        dir.mkdirs()
        val file = File(dir, "$RECORDING_PREFIX${clock()}_${counter.incrementAndGet()}.wav")
        active[file.absolutePath] = clock()
        return file
    }

    /** Whether [file] is a store file (so history can own it). */
    fun contains(file: File): Boolean = isInside(file)

    /** The take is referenced by a row now (or gone); orphan cleanup may judge it from here on. */
    fun release(path: String) {
        active.remove(path)
    }

    fun delete(path: String) {
        val file = File(path)
        if (isInside(file)) file.delete()
        active.remove(path)
    }

    fun deleteAll(paths: Collection<String>) = paths.forEach(::delete)

    /** Files no row points at, not in use and untouched since [modifiedBefore]: abandoned by a dead run. */
    fun orphans(referenced: Set<String>, modifiedBefore: Long): List<File> {
        pruneActive()
        return dir.listFiles().orEmpty().filter { file ->
            file.isFile && file.absolutePath !in referenced && !active.containsKey(file.absolutePath) &&
                file.lastModified() < modifiedBefore
        }
    }

    /** Removes [orphans]; use [orphans] first to salvage the ones that hold audio. */
    fun deleteOrphans(referenced: Set<String>, modifiedBefore: Long) {
        orphans(referenced, modifiedBefore).forEach { it.delete() }
    }

    /** Removes every file except takes in use. */
    fun clear() {
        pruneActive()
        dir.listFiles()?.forEach { if (it.isFile && !active.containsKey(it.absolutePath)) it.delete() }
    }

    /**
     * Gives a WAV whose recorder never wrote its header (killed process, full disk) a valid one so
     * it plays and uploads. False when [file] holds no audio.
     */
    fun repairWavHeader(file: File): Boolean {
        if (!isInside(file) || file.length() <= WAV_HEADER_BYTES) return false
        return try {
            RandomAccessFile(file, "rw").use { raf ->
                val magic = ByteArray(4)
                raf.seek(0)
                val read = raf.read(magic)
                if (read != magic.size || !magic.contentEquals(RIFF_MAGIC)) {
                    raf.seek(0)
                    raf.write(RiffWaveHelper.headerBytes(raf.length().toInt()))
                }
            }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun pruneActive() {
        // A registered path whose file never appeared (start failed) or was removed by the session.
        val cutoff = clock() - REGISTRATION_GRACE_MS
        active.entries.removeAll { (path, since) -> since < cutoff && !File(path).exists() }
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
        const val RECORDING_PREFIX = "rec_"
        const val WAV_HEADER_BYTES = 44L

        /** 16 kHz mono PCM16: 32 bytes per millisecond. */
        const val BYTES_PER_MS = 32L
        private const val REGISTRATION_GRACE_MS = 10 * 60 * 1000L

        private val RIFF_MAGIC = "RIFF".toByteArray(Charsets.US_ASCII)

        /** Audio length of a recorder WAV, from its size. */
        fun durationMs(file: File): Long = ((file.length() - WAV_HEADER_BYTES) / BYTES_PER_MS).coerceAtLeast(0L)
    }
}
