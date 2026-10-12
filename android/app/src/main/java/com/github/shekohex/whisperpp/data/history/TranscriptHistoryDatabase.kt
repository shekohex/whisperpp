package com.github.shekohex.whisperpp.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

const val HISTORY_DATABASE_NAME = "history.db"

/** Excluded from Android auto backup (backup_rules / data_extraction_rules); exported only via the opt-in settings backup category. */
@Database(entities = [TranscriptHistoryEntry::class], version = 1, exportSchema = false)
abstract class TranscriptHistoryDatabase : RoomDatabase() {
    abstract fun dao(): TranscriptHistoryDao

    companion object {
        fun create(context: Context): TranscriptHistoryDatabase =
            Room.databaseBuilder(context.applicationContext, TranscriptHistoryDatabase::class.java, HISTORY_DATABASE_NAME)
                .build()
    }
}
