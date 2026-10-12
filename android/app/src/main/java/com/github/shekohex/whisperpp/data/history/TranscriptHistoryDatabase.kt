package com.github.shekohex.whisperpp.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

const val HISTORY_DATABASE_NAME = "history.db"

/**
 * Excluded from Android auto backup (backup_rules / data_extraction_rules); exported only via the
 * opt-in settings backup category. Version 2 adds status, failure reason and mode per take; see
 * [TranscriptHistoryMigrations]. There is deliberately no destructive fallback.
 */
@Database(entities = [TranscriptHistoryEntry::class], version = 2, exportSchema = false)
abstract class TranscriptHistoryDatabase : RoomDatabase() {
    abstract fun dao(): TranscriptHistoryDao

    companion object {
        fun create(context: Context): TranscriptHistoryDatabase =
            Room.databaseBuilder(context.applicationContext, TranscriptHistoryDatabase::class.java, HISTORY_DATABASE_NAME)
                .addMigrations(*TranscriptHistoryMigrations.ALL)
                .build()
    }
}
