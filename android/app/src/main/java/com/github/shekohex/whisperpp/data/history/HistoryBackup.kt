package com.github.shekohex.whisperpp.data.history

import com.github.shekohex.whisperpp.domain.dictation.HistoryFailureReason
import com.github.shekohex.whisperpp.domain.dictation.HistoryStatus

const val HISTORY_BACKUP_SCHEMA_VERSION = 1

/**
 * History export (standalone JSON and the opt-in settings-backup `history` category). Never carries
 * audio. Status, failure reason and mode are additive optional fields, so schema 1 files still load.
 */
data class HistoryBackup(
    val schemaVersion: Int = HISTORY_BACKUP_SCHEMA_VERSION,
    val entries: List<HistoryBackupEntry> = emptyList(),
)

data class HistoryBackupEntry(
    val createdAt: Long = 0,
    val rawText: String = "",
    val enhancedText: String? = null,
    val providerId: String = "",
    val sttModelId: String = "",
    val enhancementModelId: String? = null,
    val language: String? = null,
    val appPackage: String? = null,
    val durationMs: Long = 0,
    val wordCount: Int = 0,
    /** [HistoryStatus] name; null (older exports) means COMPLETED. */
    val status: String? = null,
    /** [HistoryFailureReason] name. */
    val failureReason: String? = null,
    val modeId: String? = null,
)

fun TranscriptHistoryEntry.toBackupEntry(): HistoryBackupEntry = HistoryBackupEntry(
    createdAt = createdAt,
    rawText = rawText,
    enhancedText = enhancedText,
    providerId = providerId,
    sttModelId = sttModelId,
    enhancementModelId = enhancementModelId,
    language = language,
    appPackage = appPackage,
    durationMs = durationMs,
    wordCount = wordCount,
    status = status.name,
    failureReason = failureReason?.name,
    modeId = modeId,
)

/**
 * Drops entries without text: a take without a transcript is worthless once its audio (never exported)
 * is missing. Gson may leave non-null Kotlin fields null on malformed input.
 */
@Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
fun sanitizeHistoryBackup(backup: HistoryBackup): HistoryBackup = backup.copy(
    entries = backup.entries.orEmpty().mapNotNull { entry ->
        if (entry == null || entry.rawText.isNullOrBlank() || entry.createdAt <= 0L) return@mapNotNull null
        entry.copy(
            providerId = entry.providerId ?: "",
            sttModelId = entry.sttModelId ?: "",
            durationMs = entry.durationMs.coerceAtLeast(0L),
            wordCount = entry.wordCount.coerceAtLeast(0),
            enhancedText = entry.enhancedText?.takeIf { it.isNotBlank() },
        )
    },
)

fun HistoryBackupEntry.toEntry(): TranscriptHistoryEntry = TranscriptHistoryEntry(
    createdAt = createdAt,
    rawText = rawText,
    enhancedText = enhancedText,
    providerId = providerId,
    sttModelId = sttModelId,
    enhancementModelId = enhancementModelId,
    language = language,
    appPackage = appPackage,
    durationMs = durationMs,
    wordCount = wordCount,
    audioPath = null,
    // Without its audio nothing is left to retry, so a restored take never asks for attention.
    status = HistoryStatus.COMPLETED,
    failureReason = HistoryFailureReason.fromName(failureReason)?.takeIf { it == HistoryFailureReason.ENHANCEMENT_FAILED },
    modeId = modeId,
)
