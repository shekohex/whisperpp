package com.github.shekohex.whisperpp.analytics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.github.shekohex.whisperpp.data.PreferenceKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class AnalyticsRepositoryTest {
    private val now: Instant = Instant.parse("2026-03-09T10:15:00Z")
    private val today = LocalDate.of(2026, 3, 9)
    private val utcClock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun recordCompletedSession_updatesTotalsAndCurrentDayBucket() = runBlocking {
        val repository = AnalyticsRepository(dataStore = MutablePreferencesDataStore(), clock = utcClock)

        repository.recordCompletedSession(
            durationMs = 120_000,
            rawText = "hello there world again",
            finalInsertedText = "hello brave new world again now",
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
        assertEquals(6, bucket.finalInsertedWordCount)
        assertEquals(1, bucket.hourlySessions[10])
    }

    @Test
    fun recordCancelledSession_onlyIncrementsCancelledTotals() = runBlocking {
        val repository = AnalyticsRepository(dataStore = MutablePreferencesDataStore(), clock = utcClock)

        repository.recordCancelledSession(durationMs = 95_000)

        val snapshot = repository.snapshot.first()
        val bucket = snapshot.dailyBuckets.single { it.date == today }
        assertEquals(0, snapshot.totalCompletedSessions)
        assertEquals(1, snapshot.totalCancelledSessions)
        assertEquals(0L, snapshot.lifetimeSessions)
        assertEquals(1, bucket.cancelledSessionCount)
        assertEquals(0, bucket.completedSessionCount)
    }

    @Test
    fun resetAnalytics_zeroesSnapshotAndHistory() = runBlocking {
        val repository = AnalyticsRepository(dataStore = MutablePreferencesDataStore(), clock = utcClock)
        repository.recordCompletedSession(60_000, "one two three", "one two three four", now.minus(Duration.ofDays(1)))
        repository.recordCancelledSession(30_000)

        repository.resetAnalytics()

        val snapshot = repository.snapshot.first()
        assertEquals(0, snapshot.totalCompletedSessions)
        assertEquals(0, snapshot.totalCancelledSessions)
        assertEquals(0L, snapshot.lifetimeWords)
        assertEquals(0L, snapshot.lifetimeTimeSavedSeconds)
        assertEquals(List(24) { 0 }, snapshot.lifetimeHourlySessions)
        assertTrue(snapshot.modelUsage.isEmpty())
        assertTrue(snapshot.activityCalendar.all { it.completedSessionCount == 0 && it.cancelledSessionCount == 0 })
    }

    @Test
    fun timeSaved_isPerSessionMaxOfTypingMinusRecording() {
        // 80 words at 40 wpm = 120 s of typing, minus 30 s spoken.
        assertEquals(90L, AnalyticsRepository.timeSavedSeconds(words = 80, recordingDurationMillis = 30_000))
        // Slower than typing never goes negative.
        assertEquals(0L, AnalyticsRepository.timeSavedSeconds(words = 10, recordingDurationMillis = 60_000))
        assertEquals(0L, AnalyticsRepository.timeSavedSeconds(words = 0, recordingDurationMillis = 0))
    }

    @Test
    fun lifetimeTotals_sumPerSessionTimeSaved_andOutliveTheRollingWindow() = runBlocking {
        val repository = AnalyticsRepository(dataStore = MutablePreferencesDataStore(), clock = utcClock)
        repository.recordCompletedSession(30_000, words(80), words(80))
        repository.recordCompletedSession(60_000, words(10), words(10))
        // Long outside the 16-week window: only lifetime counters see it.
        repository.recordCompletedSession(20_000, words(40), words(40), now.minus(Duration.ofDays(200)))

        val snapshot = repository.snapshot.first()
        assertEquals(130L, snapshot.lifetimeWords)
        assertEquals(3L, snapshot.lifetimeSessions)
        assertEquals(110L, snapshot.lifetimeRecordingSeconds)
        // 90 + 0 + (60 - 20)
        assertEquals(130L, snapshot.lifetimeTimeSavedSeconds)
        assertEquals(2, snapshot.estimatedTimeSavedMinutes)
        assertEquals(AnalyticsRepository.WINDOW_DAYS, snapshot.activityCalendar.size)
        assertEquals(90L, snapshot.periodSnapshot(AnalyticsPeriod.LAST_7_DAYS).timeSavedSeconds)
        assertEquals(130L, snapshot.periodSnapshot(AnalyticsPeriod.ALL).timeSavedSeconds)
    }

    @Test
    fun migration_seedsLifetimeFromDailyBucketsOnce() = runBlocking {
        val v1Buckets = """
            [{"date":"2026-03-08","completedSessionCount":2,"cancelledSessionCount":1,"recordingDurationMillis":40000,
              "rawWordCount":100,"finalInsertedWordCount":100,"insertedCharacterCount":500,"estimatedTimeSavedMinutes":2},
             {"date":"2026-03-09","completedSessionCount":1,"recordingDurationMillis":20000,
              "rawWordCount":50,"finalInsertedWordCount":50,"insertedCharacterCount":250,"estimatedTimeSavedMinutes":1}]
        """.trimIndent()
        val store = MutablePreferencesDataStore(
            mutablePreferencesOf(PreferenceKeys.Analytics.DAILY_BUCKETS_JSON to v1Buckets)
        )
        val repository = AnalyticsRepository(dataStore = store, clock = utcClock)

        // Readable before the migration ran.
        assertEquals(150L, repository.snapshot.first().lifetimeWords)

        repository.migrateIfNeeded()
        repository.migrateIfNeeded()

        val prefs = store.data.first()
        assertEquals(AnalyticsRepository.SCHEMA_VERSION, prefs[PreferenceKeys.Analytics.SCHEMA_VERSION])
        assertEquals(150L, prefs[PreferenceKeys.Analytics.LIFETIME_WORDS])
        assertEquals(3L, prefs[PreferenceKeys.Analytics.LIFETIME_SESSIONS])
        assertEquals(60L, prefs[PreferenceKeys.Analytics.LIFETIME_RECORDING_SECONDS])
        // Sum of the buckets' time saved (2 + 1 min); there are no v1 totals to compare against.
        assertEquals(180L, prefs[PreferenceKeys.Analytics.LIFETIME_TIME_SAVED_SECONDS])

        repository.recordCompletedSession(30_000, words(80), words(80))
        val snapshot = repository.snapshot.first()
        assertEquals(230L, snapshot.lifetimeWords)
        assertEquals(4L, snapshot.lifetimeSessions)
        assertEquals(270L, snapshot.lifetimeTimeSavedSeconds)
        assertEquals(2, snapshot.activityCalendar.single { it.date == today }.completedSessionCount)
    }

    @Test
    fun migration_prefersLifetimeTotalsWhenLargerThanBuckets() = runBlocking {
        val store = MutablePreferencesDataStore(
            mutablePreferencesOf(
                PreferenceKeys.Analytics.TOTAL_COMPLETED_SESSIONS to 10,
                PreferenceKeys.Analytics.TOTAL_FINAL_INSERTED_WORD_COUNT to 1_000,
                PreferenceKeys.Analytics.TOTAL_RECORDING_DURATION_MILLIS to 300_000L,
            )
        )
        val repository = AnalyticsRepository(dataStore = store, clock = utcClock)

        repository.migrateIfNeeded()

        val snapshot = repository.snapshot.first()
        assertEquals(1_000L, snapshot.lifetimeWords)
        assertEquals(10L, snapshot.lifetimeSessions)
        assertEquals(300L, snapshot.lifetimeRecordingSeconds)
        assertEquals(1_200L, snapshot.lifetimeTimeSavedSeconds)
    }

    @Test
    fun hourBinning_usesTheClockZone() = runBlocking {
        val instant = Instant.parse("2026-03-09T23:30:00Z")
        val utc = AnalyticsRepository(MutablePreferencesDataStore(), clock = Clock.fixed(instant, ZoneOffset.UTC))
        val tokyo = AnalyticsRepository(MutablePreferencesDataStore(), clock = Clock.fixed(instant, ZoneId.of("Asia/Tokyo")))
        val newYork = AnalyticsRepository(MutablePreferencesDataStore(), clock = Clock.fixed(instant, ZoneId.of("America/New_York")))

        listOf(utc, tokyo, newYork).forEach { it.recordCompletedSession(10_000, "a b", "a b", instant) }

        val utcSnapshot = utc.snapshot.first()
        val tokyoSnapshot = tokyo.snapshot.first()
        val newYorkSnapshot = newYork.snapshot.first()
        assertEquals(1, utcSnapshot.lifetimeHourlySessions[23])
        assertEquals(1, tokyoSnapshot.lifetimeHourlySessions[8])
        assertEquals(1, newYorkSnapshot.lifetimeHourlySessions[19])
        assertEquals(1, utcSnapshot.lifetimeHourlySessions.sum())
        // Tokyo is already on the next day.
        assertEquals(LocalDate.of(2026, 3, 10), tokyoSnapshot.activityCalendar.last { it.completedSessionCount > 0 }.date)
        assertEquals(1, tokyoSnapshot.periodSnapshot(AnalyticsPeriod.LAST_7_DAYS).hourlySessions[8])
        assertEquals(LocalDate.of(2026, 3, 9), utcSnapshot.activityCalendar.last().date)
    }

    @Test
    fun modelBuckets_trackSessionsWordsAndLatencies_perPeriod() = runBlocking {
        val repository = AnalyticsRepository(dataStore = MutablePreferencesDataStore(), clock = utcClock)
        val whisper = AnalyticsModelSample(
            providerId = "openai",
            modelId = "whisper-1",
            transcriptionLatencyMs = 800,
            enhancementProviderId = "openai",
            enhancementModelId = "gpt",
            enhancementLatencyMs = 1_200,
        )
        repository.recordCompletedSession(10_000, words(5), words(5), model = whisper)
        repository.recordCompletedSession(10_000, words(3), words(3), model = whisper.copy(transcriptionLatencyMs = 400, enhancementModelId = null))
        repository.recordCompletedSession(
            10_000,
            words(2),
            words(2),
            now.minus(Duration.ofDays(10)),
            AnalyticsModelSample("groq", "turbo", 100),
        )

        val all = repository.snapshot.first().periodSnapshot(AnalyticsPeriod.ALL).modelUsage
        val stt = all.single { it.key == "openai/whisper-1" }
        assertEquals(2, stt.sessions)
        assertEquals(8L, stt.words)
        assertEquals(600L, stt.averageTranscriptionLatencyMs)
        assertNull(stt.averageEnhancementLatencyMs)
        val gpt = all.single { it.key == "openai/gpt" }
        assertEquals(0, gpt.sessions)
        assertEquals(1, gpt.enhancementSessions)
        assertEquals(1_200L, gpt.averageEnhancementLatencyMs)
        assertEquals("openai/whisper-1", all.first().key)

        val week = repository.snapshot.first().periodSnapshot(AnalyticsPeriod.LAST_7_DAYS)
        assertTrue(week.modelUsage.none { it.key == "groq/turbo" })
        assertEquals(2L, week.sessions)
        val month = repository.snapshot.first().periodSnapshot(AnalyticsPeriod.LAST_30_DAYS)
        assertEquals(1, month.modelUsage.single { it.key == "groq/turbo" }.sessions)
        assertEquals(30, month.dailyBuckets.size)
    }

    @Test
    fun activityCalendar_isSixteenWeeksEndingToday_zeroFilled() = runBlocking {
        val repository = AnalyticsRepository(dataStore = MutablePreferencesDataStore(), clock = utcClock)
        repository.recordCompletedSession(10_000, "a", "a", now.minus(Duration.ofDays(30)))

        val calendar = repository.snapshot.first().activityCalendar
        assertEquals(112, calendar.size)
        assertEquals(today, calendar.last().date)
        assertEquals(today.minusDays(111), calendar.first().date)
        assertEquals(1, calendar.sumOf { it.completedSessionCount })
        assertEquals(1, calendar.single { it.date == today.minusDays(30) }.completedSessionCount)
    }

    private fun words(count: Int): String = List(count) { "word" }.joinToString(" ")

    private class MutablePreferencesDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
