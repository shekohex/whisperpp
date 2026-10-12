package com.github.shekohex.whisperpp.data.history

import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.shekohex.whisperpp.domain.dictation.HistoryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/**
 * The v1 -> v2 migration at statement level (Room itself needs a device; the on-device run is
 * `TranscriptHistoryMigrationInstrumentedTest`): additive only, every new column covered, and the
 * existing rows stay valid.
 */
class TranscriptHistoryMigrationsTest {
    private val v1Columns = setOf(
        "id", "createdAt", "rawText", "enhancedText", "providerId", "sttModelId",
        "enhancementModelId", "language", "appPackage", "durationMs", "wordCount", "audioPath",
    )

    private fun entityColumns(): Set<String> = TranscriptHistoryEntry::class.java.declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
        .map { it.name }
        .toSet()

    @Test
    fun migration_goesFromVersion1To2() {
        assertEquals(1, TranscriptHistoryMigrations.MIGRATION_1_2.startVersion)
        assertEquals(2, TranscriptHistoryMigrations.MIGRATION_1_2.endVersion)
        assertEquals(listOf(TranscriptHistoryMigrations.MIGRATION_1_2), TranscriptHistoryMigrations.ALL.toList())
    }

    @Test
    fun everyNewEntityColumn_isAddedByTheMigration() {
        val added = entityColumns() - v1Columns
        assertEquals(setOf("status", "failureReason", "modeId"), added)

        val sql = TranscriptHistoryMigrations.MIGRATION_1_2_SQL
        added.forEach { column ->
            assertEquals(column, 1, sql.count { it.contains("ADD COLUMN `$column`") })
        }
        assertEquals(added.size, sql.size)
    }

    @Test
    fun migration_isAdditiveOnly() {
        TranscriptHistoryMigrations.MIGRATION_1_2_SQL.forEach { statement ->
            assertTrue(statement, statement.startsWith("ALTER TABLE `transcript_history` ADD COLUMN"))
            assertFalse(statement, statement.contains("DROP", ignoreCase = true))
            assertFalse(statement, statement.contains("DELETE", ignoreCase = true))
        }
    }

    @Test
    fun existingRows_becomeCompleted() {
        val status = TranscriptHistoryMigrations.MIGRATION_1_2_SQL.single { it.contains("`status`") }
        assertTrue(status, status.contains("NOT NULL DEFAULT 'COMPLETED'"))
        // Nullable columns need no default: old rows read null.
        TranscriptHistoryMigrations.MIGRATION_1_2_SQL.filterNot { it.contains("`status`") }.forEach {
            assertFalse(it, it.contains("NOT NULL"))
        }
    }

    @Test
    fun entityDefault_matchesTheMigrationDefault() {
        val entry = TranscriptHistoryEntry(createdAt = 1, rawText = "x", providerId = "p", sttModelId = "m", durationMs = 1, wordCount = 1)
        assertEquals(HistoryStatus.COMPLETED, entry.status)
    }

    @Test
    fun migrate_runsEveryStatementInOrder() {
        val executed = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL") executed += args[0] as String
            null
        } as SupportSQLiteDatabase

        TranscriptHistoryMigrations.MIGRATION_1_2.migrate(db)

        assertEquals(TranscriptHistoryMigrations.MIGRATION_1_2_SQL, executed)
    }
}
