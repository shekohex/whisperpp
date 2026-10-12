package com.github.shekohex.whisperpp.data.history

const val HISTORY_BACKUP_SCHEMA_VERSION = 1

/** Text-only history export (standalone JSON and the opt-in settings-backup `history` category). Never carries audio. */
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
)

/** Drops entries without text; Gson may leave non-null Kotlin fields null on malformed input. */
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
)
