package com.github.shekohex.whisperpp.data.dictionary

import com.github.shekohex.whisperpp.core.text.Replacement

const val DICTIONARY_BACKUP_SCHEMA_VERSION = 1

/** Dictionary export (standalone JSON file and the settings-backup `dictionary` category). */
data class DictionaryBackup(
    val schemaVersion: Int = DICTIONARY_BACKUP_SCHEMA_VERSION,
    val replacements: List<Replacement> = emptyList(),
    val vocabulary: List<String> = emptyList(),
)
