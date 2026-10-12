package com.github.shekohex.whisperpp.analytics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.core.text.WordCounter
import com.github.shekohex.whisperpp.data.PreferenceKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Dictation analytics (§4.9) in the `analytics` DataStore: lifetime counters, a 16-week window of
 * daily buckets (with hour-of-day and per-model breakdowns), lifetime hourly bins and per-model
 * buckets. Dates and hours use [clock]'s zone at record time. Never stores transcript text.
 */
class AnalyticsRepository(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson(),
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    val snapshot: Flow<AnalyticsSnapshot> = dataStore.data.map { preferences -> buildSnapshot(preferences) }

    /** One-time v1 → v2 schema step; also applied lazily by every write. */
    suspend fun migrateIfNeeded() {
        dataStore.edit { preferences -> migrate(preferences) }
    }

    suspend fun recordCompletedSession(
        durationMs: Long,
        rawText: String,
        finalInsertedText: String,
        recordedAt: Instant = clock.instant(),
        model: AnalyticsModelSample? = null,
    ) {
        val normalizedDuration = max(durationMs, 0L)
        val rawWordCount = WordCounter.count(rawText)
        val finalWordCount = WordCounter.count(finalInsertedText)
        val insertedCharacterCount = finalInsertedText.length
        val timeSavedSeconds = timeSavedSeconds(finalWordCount.toLong(), normalizedDuration)
        val local = recordedAt.atZone(clock.zone)
        val recordedOn = local.toLocalDate()
        val hour = local.hour
        val modelUsage = model?.let { ModelUsage.fromSample(it, finalWordCount) }.orEmpty()

        dataStore.edit { preferences ->
            migrate(preferences)
            preferences.add(PreferenceKeys.Analytics.TOTAL_COMPLETED_SESSIONS, 1)
            preferences.add(PreferenceKeys.Analytics.TOTAL_RECORDING_DURATION_MILLIS, normalizedDuration)
            preferences.add(PreferenceKeys.Analytics.TOTAL_RAW_WORD_COUNT, rawWordCount)
            preferences.add(PreferenceKeys.Analytics.TOTAL_FINAL_INSERTED_WORD_COUNT, finalWordCount)
            preferences.add(PreferenceKeys.Analytics.TOTAL_INSERTED_CHARACTER_COUNT, insertedCharacterCount)

            preferences.add(PreferenceKeys.Analytics.LIFETIME_SESSIONS, 1L)
            preferences.add(PreferenceKeys.Analytics.LIFETIME_WORDS, finalWordCount.toLong())
            preferences.add(PreferenceKeys.Analytics.LIFETIME_RECORDING_SECONDS, (normalizedDuration / 1_000.0).roundToLong())
            preferences.add(PreferenceKeys.Analytics.LIFETIME_TIME_SAVED_SECONDS, timeSavedSeconds)

            val hourly = parseHourly(preferences[PreferenceKeys.Analytics.LIFETIME_HOURLY_JSON]).toMutableList()
            hourly[hour] += 1
            preferences[PreferenceKeys.Analytics.LIFETIME_HOURLY_JSON] = gson.toJson(hourly)

            if (modelUsage.isNotEmpty()) {
                val models = ModelUsage.merge(parseModels(preferences[PreferenceKeys.Analytics.MODEL_BUCKETS_JSON]) + modelUsage)
                preferences[PreferenceKeys.Analytics.MODEL_BUCKETS_JSON] = encodeModels(models)
            }

            updateBucket(preferences, recordedOn) { bucket ->
                val bucketHourly = bucket.hourlySessions.takeIf { it.size == HOURS }?.toMutableList() ?: MutableList(HOURS) { 0 }
                bucketHourly[hour] += 1
                bucket.copy(
                    completedSessionCount = bucket.completedSessionCount + 1,
                    recordingDurationMillis = bucket.recordingDurationMillis + normalizedDuration,
                    rawWordCount = bucket.rawWordCount + rawWordCount,
                    finalInsertedWordCount = bucket.finalInsertedWordCount + finalWordCount,
                    insertedCharacterCount = bucket.insertedCharacterCount + insertedCharacterCount,
                    estimatedTimeSavedMinutes = bucket.estimatedTimeSavedMinutes + secondsToRoundedMinutes(timeSavedSeconds),
                    timeSavedSeconds = bucket.timeSavedSeconds + timeSavedSeconds,
                    hourlySessions = bucketHourly,
                    modelUsage = ModelUsage.merge(bucket.modelUsage + modelUsage),
                )
            }
        }
    }

    suspend fun recordCancelledSession(durationMs: Long, recordedAt: Instant = clock.instant()) {
        val recordedOn = recordedAt.atZone(clock.zone).toLocalDate()
        dataStore.edit { preferences ->
            migrate(preferences)
            preferences.add(PreferenceKeys.Analytics.TOTAL_CANCELLED_SESSIONS, 1)
            updateBucket(preferences, recordedOn) { it.copy(cancelledSessionCount = it.cancelledSessionCount + 1) }
        }
    }

    suspend fun recordOutcome(outcome: DictationAnalyticsSession.Outcome, recordedAt: Instant = clock.instant()) {
        when (outcome) {
            is DictationAnalyticsSession.Outcome.Completed -> recordCompletedSession(
                durationMs = outcome.durationMs,
                rawText = outcome.rawText,
                finalInsertedText = outcome.finalInsertedText,
                recordedAt = recordedAt,
                model = outcome.model,
            )
            is DictationAnalyticsSession.Outcome.Cancelled -> recordCancelledSession(outcome.durationMs, recordedAt)
        }
    }

    suspend fun resetAnalytics() {
        dataStore.edit { preferences ->
            preferences.clear()
            preferences[PreferenceKeys.Analytics.SCHEMA_VERSION] = SCHEMA_VERSION
            preferences[PreferenceKeys.Analytics.DAILY_BUCKETS_JSON] = encodeBuckets(normalizeBuckets(emptyList(), today()))
        }
    }

    private fun buildSnapshot(preferences: Preferences): AnalyticsSnapshot {
        val calendar = normalizeBuckets(parseBuckets(preferences[PreferenceKeys.Analytics.DAILY_BUCKETS_JSON]), today())
        val lifetime = if ((preferences[PreferenceKeys.Analytics.SCHEMA_VERSION] ?: 1) >= SCHEMA_VERSION) {
            Lifetime.read(preferences)
        } else {
            // Not migrated yet (first read after an update): show what the migration will seed.
            seedLifetime(preferences, calendar)
        }
        val totalRecordingDurationMillis = preferences[PreferenceKeys.Analytics.TOTAL_RECORDING_DURATION_MILLIS] ?: 0L
        val totalFinalInsertedWordCount = preferences[PreferenceKeys.Analytics.TOTAL_FINAL_INSERTED_WORD_COUNT] ?: 0
        val totalInsertedCharacterCount = preferences[PreferenceKeys.Analytics.TOTAL_INSERTED_CHARACTER_COUNT] ?: 0

        return AnalyticsSnapshot(
            totalCompletedSessions = preferences[PreferenceKeys.Analytics.TOTAL_COMPLETED_SESSIONS] ?: 0,
            totalCancelledSessions = preferences[PreferenceKeys.Analytics.TOTAL_CANCELLED_SESSIONS] ?: 0,
            totalRecordingDurationMillis = totalRecordingDurationMillis,
            totalRecordingDurationMinutes = durationMillisToRoundedMinutes(totalRecordingDurationMillis),
            totalRawWordCount = preferences[PreferenceKeys.Analytics.TOTAL_RAW_WORD_COUNT] ?: 0,
            totalFinalInsertedWordCount = totalFinalInsertedWordCount,
            averageWordsPerMinute = AnalyticsFormatter.calculateWordsPerMinute(
                finalInsertedWords = totalFinalInsertedWordCount,
                durationMillis = totalRecordingDurationMillis,
            ),
            totalInsertedCharacterCount = totalInsertedCharacterCount,
            estimatedKeystrokesSaved = AnalyticsFormatter.estimateKeystrokesSaved(totalInsertedCharacterCount),
            estimatedTimeSavedMinutes = secondsToRoundedMinutes(lifetime.timeSavedSeconds),
            dailyBuckets = calendar.takeLast(LEGACY_WINDOW_DAYS),
            lifetimeWords = lifetime.words,
            lifetimeSessions = lifetime.sessions,
            lifetimeRecordingSeconds = lifetime.recordingSeconds,
            lifetimeTimeSavedSeconds = lifetime.timeSavedSeconds,
            lifetimeHourlySessions = parseHourly(preferences[PreferenceKeys.Analytics.LIFETIME_HOURLY_JSON]),
            modelUsage = ModelUsage.merge(parseModels(preferences[PreferenceKeys.Analytics.MODEL_BUCKETS_JSON])),
            activityCalendar = calendar,
        )
    }

    private fun migrate(preferences: MutablePreferences) {
        if ((preferences[PreferenceKeys.Analytics.SCHEMA_VERSION] ?: 1) >= SCHEMA_VERSION) return
        val buckets = parseBuckets(preferences[PreferenceKeys.Analytics.DAILY_BUCKETS_JSON])
        val seeded = seedLifetime(preferences, buckets)
        preferences[PreferenceKeys.Analytics.LIFETIME_WORDS] = seeded.words
        preferences[PreferenceKeys.Analytics.LIFETIME_SESSIONS] = seeded.sessions
        preferences[PreferenceKeys.Analytics.LIFETIME_RECORDING_SECONDS] = seeded.recordingSeconds
        preferences[PreferenceKeys.Analytics.LIFETIME_TIME_SAVED_SECONDS] = seeded.timeSavedSeconds
        preferences[PreferenceKeys.Analytics.SCHEMA_VERSION] = SCHEMA_VERSION
    }

    /**
     * v1 seed: the sum of the daily buckets. The v1 `TOTAL_*` counters were already lifetime, so
     * words/sessions/recording take the larger of the two; time saved takes the larger of the bucket
     * sum and the estimate from the totals (both are lower bounds of the per-session sum).
     */
    private fun seedLifetime(preferences: Preferences, buckets: List<AnalyticsDayBucket>): Lifetime {
        val bucketWords = buckets.sumOf { it.finalInsertedWordCount.toLong() }
        val bucketSessions = buckets.sumOf { it.completedSessionCount.toLong() }
        val bucketRecordingMs = buckets.sumOf { it.recordingDurationMillis }
        val bucketTimeSaved = buckets.sumOf { max(it.timeSavedSeconds, it.estimatedTimeSavedMinutes * 60L) }
        val totalWords = (preferences[PreferenceKeys.Analytics.TOTAL_FINAL_INSERTED_WORD_COUNT] ?: 0).toLong()
        val totalSessions = (preferences[PreferenceKeys.Analytics.TOTAL_COMPLETED_SESSIONS] ?: 0).toLong()
        val totalRecordingMs = preferences[PreferenceKeys.Analytics.TOTAL_RECORDING_DURATION_MILLIS] ?: 0L
        val words = max(bucketWords, totalWords)
        val recordingMs = max(bucketRecordingMs, totalRecordingMs)
        return Lifetime(
            words = words,
            sessions = max(bucketSessions, totalSessions),
            recordingSeconds = (recordingMs / 1_000.0).roundToLong(),
            timeSavedSeconds = max(bucketTimeSaved, timeSavedSeconds(totalWords, totalRecordingMs)),
        )
    }

    private fun updateBucket(
        preferences: MutablePreferences,
        date: LocalDate,
        transform: (AnalyticsDayBucket) -> AnalyticsDayBucket,
    ) {
        val updated = normalizeBuckets(parseBuckets(preferences[PreferenceKeys.Analytics.DAILY_BUCKETS_JSON]), today())
            .map { bucket -> if (bucket.date == date) transform(bucket) else bucket }
        preferences[PreferenceKeys.Analytics.DAILY_BUCKETS_JSON] = encodeBuckets(updated)
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private fun parseBuckets(json: String?): List<AnalyticsDayBucket> {
        val raw = json?.trim().orEmpty()
        if (raw.isBlank()) return emptyList()
        val type = object : TypeToken<List<StoredAnalyticsDayBucket>>() {}.type
        return try {
            gson.fromJson<List<StoredAnalyticsDayBucket?>>(raw, type)
                .orEmpty()
                .mapNotNull { stored -> stored?.toBucket() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun StoredAnalyticsDayBucket.toBucket(): AnalyticsDayBucket? {
        val parsedDate = try {
            LocalDate.parse(date)
        } catch (_: DateTimeParseException) {
            return null
        }
        return AnalyticsDayBucket(
            date = parsedDate,
            completedSessionCount = completedSessionCount,
            cancelledSessionCount = cancelledSessionCount,
            recordingDurationMillis = recordingDurationMillis,
            rawWordCount = rawWordCount,
            finalInsertedWordCount = finalInsertedWordCount,
            insertedCharacterCount = insertedCharacterCount,
            estimatedTimeSavedMinutes = estimatedTimeSavedMinutes,
            timeSavedSeconds = timeSavedSeconds,
            hourlySessions = hourlySessions?.takeIf { it.size == HOURS }.orEmpty(),
            modelUsage = models.orEmpty().mapNotNull { it?.toUsage() },
        )
    }

    private fun encodeBuckets(buckets: List<AnalyticsDayBucket>): String {
        return gson.toJson(
            buckets.map { bucket ->
                StoredAnalyticsDayBucket(
                    date = bucket.date.toString(),
                    completedSessionCount = bucket.completedSessionCount,
                    cancelledSessionCount = bucket.cancelledSessionCount,
                    recordingDurationMillis = bucket.recordingDurationMillis,
                    rawWordCount = bucket.rawWordCount,
                    finalInsertedWordCount = bucket.finalInsertedWordCount,
                    insertedCharacterCount = bucket.insertedCharacterCount,
                    estimatedTimeSavedMinutes = bucket.estimatedTimeSavedMinutes,
                    timeSavedSeconds = bucket.timeSavedSeconds,
                    hourlySessions = bucket.hourlySessions.takeIf { it.isNotEmpty() },
                    models = bucket.modelUsage.takeIf { it.isNotEmpty() }?.map(StoredModelUsage::from),
                )
            }
        )
    }

    private fun parseHourly(json: String?): List<Int> {
        val parsed = try {
            json?.let { gson.fromJson<List<Int?>>(it, object : TypeToken<List<Int?>>() {}.type) }
        } catch (_: Exception) {
            null
        }
        return List(HOURS) { hour -> parsed?.getOrNull(hour)?.coerceAtLeast(0) ?: 0 }
    }

    private fun parseModels(json: String?): List<ModelUsage> {
        val raw = json?.trim().orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            gson.fromJson<List<StoredModelUsage?>>(raw, object : TypeToken<List<StoredModelUsage?>>() {}.type)
                .orEmpty()
                .mapNotNull { it?.toUsage() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeModels(models: List<ModelUsage>): String = gson.toJson(models.map(StoredModelUsage::from))

    private fun normalizeBuckets(persisted: List<AnalyticsDayBucket>, today: LocalDate): List<AnalyticsDayBucket> {
        val persistedByDate = persisted.associateBy { it.date }
        return ((WINDOW_DAYS - 1).toLong() downTo 0L).map { offset ->
            val date = today.minusDays(offset)
            persistedByDate[date] ?: AnalyticsDayBucket(date = date)
        }
    }

    private fun durationMillisToRoundedMinutes(durationMillis: Long): Int {
        if (durationMillis <= 0L) return 0
        return (durationMillis / 60_000.0).roundToInt()
    }

    private data class Lifetime(
        val words: Long,
        val sessions: Long,
        val recordingSeconds: Long,
        val timeSavedSeconds: Long,
    ) {
        companion object {
            fun read(preferences: Preferences) = Lifetime(
                words = preferences[PreferenceKeys.Analytics.LIFETIME_WORDS] ?: 0L,
                sessions = preferences[PreferenceKeys.Analytics.LIFETIME_SESSIONS] ?: 0L,
                recordingSeconds = preferences[PreferenceKeys.Analytics.LIFETIME_RECORDING_SECONDS] ?: 0L,
                timeSavedSeconds = preferences[PreferenceKeys.Analytics.LIFETIME_TIME_SAVED_SECONDS] ?: 0L,
            )
        }
    }

    private data class StoredAnalyticsDayBucket(
        val date: String = "",
        val completedSessionCount: Int = 0,
        val cancelledSessionCount: Int = 0,
        val recordingDurationMillis: Long = 0,
        val rawWordCount: Int = 0,
        val finalInsertedWordCount: Int = 0,
        val insertedCharacterCount: Int = 0,
        val estimatedTimeSavedMinutes: Int = 0,
        val timeSavedSeconds: Long = 0,
        val hourlySessions: List<Int>? = null,
        val models: List<StoredModelUsage?>? = null,
    )

    private data class StoredModelUsage(
        val providerId: String? = null,
        val modelId: String? = null,
        val sessions: Int = 0,
        val words: Long = 0,
        val transcriptionLatencyMs: Long = 0,
        val enhancementSessions: Int = 0,
        val enhancementLatencyMs: Long = 0,
    ) {
        fun toUsage(): ModelUsage = ModelUsage(
            providerId = providerId.orEmpty(),
            modelId = modelId.orEmpty(),
            sessions = sessions.coerceAtLeast(0),
            words = words.coerceAtLeast(0),
            transcriptionLatencyMs = transcriptionLatencyMs.coerceAtLeast(0),
            enhancementSessions = enhancementSessions.coerceAtLeast(0),
            enhancementLatencyMs = enhancementLatencyMs.coerceAtLeast(0),
        )

        companion object {
            fun from(usage: ModelUsage) = StoredModelUsage(
                providerId = usage.providerId,
                modelId = usage.modelId,
                sessions = usage.sessions,
                words = usage.words,
                transcriptionLatencyMs = usage.transcriptionLatencyMs,
                enhancementSessions = usage.enhancementSessions,
                enhancementLatencyMs = usage.enhancementLatencyMs,
            )
        }
    }

    companion object {
        const val SCHEMA_VERSION = 2

        /** Daily buckets kept: 16 weeks (activity calendar, 90-day period). */
        const val WINDOW_DAYS = 16 * 7
        private const val LEGACY_WINDOW_DAYS = 7
        private const val HOURS = AnalyticsSnapshot.HOURS_PER_DAY

        /** `max(words / 40 wpm × 60 − recordingSeconds, 0)`, rounded to whole seconds. */
        fun timeSavedSeconds(words: Long, recordingDurationMillis: Long): Long {
            if (words <= 0L) return 0L
            val typingSeconds = words * 60.0 / AnalyticsFormatter.TYPING_BASELINE_WORDS_PER_MINUTE
            val recordingSeconds = max(recordingDurationMillis, 0L) / 1_000.0
            return max((typingSeconds - recordingSeconds).roundToLong(), 0L)
        }

        private fun secondsToRoundedMinutes(seconds: Long): Int = (seconds / 60.0).roundToInt()
    }
}

private fun MutablePreferences.add(key: Preferences.Key<Int>, delta: Int) {
    this[key] = (this[key] ?: 0) + delta
}

private fun MutablePreferences.add(key: Preferences.Key<Long>, delta: Long) {
    this[key] = (this[key] ?: 0L) + delta
}
