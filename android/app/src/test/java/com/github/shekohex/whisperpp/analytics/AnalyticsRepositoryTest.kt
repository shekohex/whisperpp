package com.github.shekohex.whisperpp.analytics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsRepositoryTest {

    @Test
    fun recordCompletedSession_updatesLifetimeTotalsAndCurrentDayBucket() = runBlocking {
        val today = LocalDate.of(2026, 3, 9)
        val repository = AnalyticsRepository(
            dataStore = MutablePreferencesDataStore(),
            todayProvider = { today },
        )

        repository.recordCompletedSession(
            durationMs = 120_000,
            rawText = "hello there world again",
            finalInsertedText = "hello brave new world again now",
            recordedOn = today,
        )

        val snapshot = repository.snapshot.first()
        val bucket = snapshot.dailyBuckets.single { it.date == today }

        assertEquals(1, snapshot.totalCompletedSessions)
        assertEquals(0, snapshot.totalCancelledSessions)
        assertEquals(2, snapshot.totalRecordingDurationMinutes)
        assertEquals(4, snapshot.totalRawWordCount)
        assertEquals(6, snapshot.totalFinalInsertedWordCount)
        assertEquals(3, snapshot.averageWordsPerMinute)
        assertEquals("hello brave new world again now".length, snapshot.estimatedKeystrokesSaved)
        assertEquals(7, snapshot.dailyBuckets.size)
        assertEquals(1, bucket.completedSessionCount)
        assertEquals(0, bucket.cancelledSessionCount)
        assertEquals(6, bucket.finalInsertedWordCount)
        assertTrue(bucket.estimatedTimeSavedMinutes >= 0)
    }

    @Test
    fun recordCancelledSession_onlyIncrementsCancelledTotals() = runBlocking {
        val today = LocalDate.of(2026, 3, 9)
        val repository = AnalyticsRepository(
            dataStore = MutablePreferencesDataStore(),
            todayProvider = { today },
        )

        repository.recordCancelledSession(
            durationMs = 95_000,
            recordedOn = today,
        )

        val snapshot = repository.snapshot.first()
        val bucket = snapshot.dailyBuckets.single { it.date == today }

        assertEquals(0, snapshot.totalCompletedSessions)
        assertEquals(1, snapshot.totalCancelledSessions)
        assertEquals(0, snapshot.totalRecordingDurationMinutes)
        assertEquals(0, snapshot.totalFinalInsertedWordCount)
        assertEquals(0, snapshot.averageWordsPerMinute)
        assertEquals(1, bucket.cancelledSessionCount)
        assertEquals(0, bucket.completedSessionCount)
    }

    @Test
    fun resetAnalytics_zeroesSnapshotAndClearsSevenDayHistory() = runBlocking {
        val today = LocalDate.of(2026, 3, 9)
        val repository = AnalyticsRepository(
            dataStore = MutablePreferencesDataStore(),
            todayProvider = { today },
        )

        repository.recordCompletedSession(
            durationMs = 60_000,
            rawText = "one two three",
            finalInsertedText = "one two three four",
            recordedOn = today.minusDays(1),
        )
        repository.recordCancelledSession(
            durationMs = 30_000,
            recordedOn = today,
        )

        repository.resetAnalytics()

        val snapshot = repository.snapshot.first()
        assertEquals(0, snapshot.totalCompletedSessions)
        assertEquals(0, snapshot.totalCancelledSessions)
        assertEquals(0, snapshot.totalRecordingDurationMinutes)
        assertEquals(0, snapshot.totalRawWordCount)
        assertEquals(0, snapshot.totalFinalInsertedWordCount)
        assertEquals(0, snapshot.averageWordsPerMinute)
        assertEquals(0, snapshot.estimatedKeystrokesSaved)
        assertEquals(7, snapshot.dailyBuckets.size)
        assertTrue(snapshot.dailyBuckets.all { bucket ->
            bucket.completedSessionCount == 0 &&
                bucket.cancelledSessionCount == 0 &&
                bucket.finalInsertedWordCount == 0 &&
                bucket.estimatedTimeSavedMinutes == 0
        })
    }

    private class MutablePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
