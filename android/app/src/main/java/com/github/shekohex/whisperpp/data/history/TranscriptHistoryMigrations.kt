package com.github.shekohex.whisperpp.data.history

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Room migrations of [TranscriptHistoryDatabase]. Never destructive: existing rows stay valid. */
object TranscriptHistoryMigrations {
    /**
     * v1 -> v2: takes are saved before transcription, so rows carry a pipeline status, a typed failure
     * reason and the mode. Existing rows are finished dictations and become COMPLETED.
     */
    val MIGRATION_1_2_SQL: List<String> = listOf(
        "ALTER TABLE `transcript_history` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'COMPLETED'",
        "ALTER TABLE `transcript_history` ADD COLUMN `failureReason` TEXT",
        "ALTER TABLE `transcript_history` ADD COLUMN `modeId` TEXT",
    )

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2_SQL.forEach(db::execSQL)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
