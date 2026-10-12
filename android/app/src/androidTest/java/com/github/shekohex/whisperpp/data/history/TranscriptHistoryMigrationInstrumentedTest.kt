package com.github.shekohex.whisperpp.data.history

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.shekohex.whisperpp.domain.dictation.HistoryFailureReason
import com.github.shekohex.whisperpp.domain.dictation.HistoryStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Opens a real version-1 history database with the version-2 Room database and its migration. */
@RunWith(AndroidJUnit4::class)
class TranscriptHistoryMigrationInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "history-migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun createVersion1Database() {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transcript_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `rawText` TEXT NOT NULL, `enhancedText` TEXT, `providerId` TEXT NOT NULL, " +
                        "`sttModelId` TEXT NOT NULL, `enhancementModelId` TEXT, `language` TEXT, `appPackage` TEXT, " +
                        "`durationMs` INTEGER NOT NULL, `wordCount` INTEGER NOT NULL, `audioPath` TEXT)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcript_history_createdAt` ON `transcript_history` (`createdAt`)")
                db.execSQL(
                    "INSERT INTO transcript_history (createdAt, rawText, enhancedText, providerId, sttModelId, durationMs, wordCount, audioPath) " +
                        "VALUES (1000, 'old words', 'Old words.', 'openai', 'whisper-1', 2000, 2, '/data/kept.wav')",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory()
            .create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build())
        helper.writableDatabase.close()
        helper.close()
    }

    @Test
    fun version1Rows_surviveAsCompletedTakes() = runBlocking {
        createVersion1Database()

        val db = Room.databaseBuilder(context, TranscriptHistoryDatabase::class.java, name)
            .addMigrations(*TranscriptHistoryMigrations.ALL)
            .build()
        try {
            val old = db.dao().all().single()
            assertEquals("old words", old.rawText)
            assertEquals("Old words.", old.enhancedText)
            assertEquals("/data/kept.wav", old.audioPath)
            assertEquals(HistoryStatus.COMPLETED, old.status)
            assertNull(old.failureReason)
            assertNull(old.modeId)

            val id = db.dao().insert(
                TranscriptHistoryEntry(
                    createdAt = 2000,
                    rawText = "",
                    providerId = "p",
                    sttModelId = "m",
                    durationMs = 1000,
                    wordCount = 0,
                    audioPath = "/data/failed.wav",
                    status = HistoryStatus.FAILED,
                    failureReason = HistoryFailureReason.NETWORK_ERROR,
                    modeId = "mode-1",
                ),
            )
            val failed = db.dao().get(id)!!
            assertEquals(HistoryStatus.FAILED, failed.status)
            assertEquals(HistoryFailureReason.NETWORK_ERROR, failed.failureReason)
            assertEquals("mode-1", failed.modeId)
        } finally {
            db.close()
        }
    }
}
