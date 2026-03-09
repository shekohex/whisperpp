package com.github.shekohex.whisperpp.analytics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.BreakIterator
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.max
import kotlin.math.roundToInt

class AnalyticsRepository(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson(),
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) {
    val snapshot: Flow<AnalyticsSnapshot> = dataStore.data.map { preferences ->
        val today = todayProvider()
        val buckets = normalizeBuckets(parseBuckets(preferences[AnalyticsStore.dailyBucketsJson]), today)
        val totalRecordingDurationMillis = preferences[AnalyticsStore.totalRecordingDurationMillis] ?: 0L
        val totalFinalInsertedWordCount = preferences[AnalyticsStore.totalFinalInsertedWordCount] ?: 0
        val totalInsertedCharacterCount = preferences[AnalyticsStore.totalInsertedCharacterCount] ?: 0

        AnalyticsSnapshot(
            totalCompletedSessions = preferences[AnalyticsStore.totalCompletedSessions] ?: 0,
            totalCancelledSessions = preferences[AnalyticsStore.totalCancelledSessions] ?: 0,
            totalRecordingDurationMillis = totalRecordingDurationMillis,
            totalRecordingDurationMinutes = durationMillisToRoundedMinutes(totalRecordingDurationMillis),
            totalRawWordCount = preferences[AnalyticsStore.totalRawWordCount] ?: 0,
            totalFinalInsertedWordCount = totalFinalInsertedWordCount,
            averageWordsPerMinute = AnalyticsFormatter.calculateWordsPerMinute(
                finalInsertedWords = totalFinalInsertedWordCount,
                durationMillis = totalRecordingDurationMillis,
            ),
            totalInsertedCharacterCount = totalInsertedCharacterCount,
            estimatedKeystrokesSaved = AnalyticsFormatter.estimateKeystrokesSaved(totalInsertedCharacterCount),
            estimatedTimeSavedMinutes = buckets.sumOf { it.estimatedTimeSavedMinutes },
            dailyBuckets = buckets,
        )
    }

    suspend fun recordCompletedSession(
        durationMs: Long,
        rawText: String,
        finalInsertedText: String,
        recordedOn: LocalDate,
    ) {
        val normalizedDuration = max(durationMs, 0L)
        val rawWordCount = countWords(rawText)
        val finalWordCount = countWords(finalInsertedText)
        val insertedCharacterCount = finalInsertedText.length
        val estimatedTimeSavedMinutes = AnalyticsFormatter.calculateTimeSavedMinutes(
            finalInsertedWords = finalWordCount,
            durationMillis = normalizedDuration,
        )

        dataStore.edit { preferences ->
            preferences[AnalyticsStore.totalCompletedSessions] = (preferences[AnalyticsStore.totalCompletedSessions] ?: 0) + 1
            preferences[AnalyticsStore.totalRecordingDurationMillis] =
                (preferences[AnalyticsStore.totalRecordingDurationMillis] ?: 0L) + normalizedDuration
            preferences[AnalyticsStore.totalRawWordCount] =
                (preferences[AnalyticsStore.totalRawWordCount] ?: 0) + rawWordCount
            preferences[AnalyticsStore.totalFinalInsertedWordCount] =
                (preferences[AnalyticsStore.totalFinalInsertedWordCount] ?: 0) + finalWordCount
            preferences[AnalyticsStore.totalInsertedCharacterCount] =
                (preferences[AnalyticsStore.totalInsertedCharacterCount] ?: 0) + insertedCharacterCount

            val updatedBuckets = normalizeBuckets(parseBuckets(preferences[AnalyticsStore.dailyBucketsJson]), todayProvider())
                .map { bucket ->
                    if (bucket.date == recordedOn) {
                        bucket.copy(
                            completedSessionCount = bucket.completedSessionCount + 1,
                            recordingDurationMillis = bucket.recordingDurationMillis + normalizedDuration,
                            rawWordCount = bucket.rawWordCount + rawWordCount,
                            finalInsertedWordCount = bucket.finalInsertedWordCount + finalWordCount,
                            insertedCharacterCount = bucket.insertedCharacterCount + insertedCharacterCount,
                            estimatedTimeSavedMinutes = bucket.estimatedTimeSavedMinutes + estimatedTimeSavedMinutes,
                        )
                    } else {
                        bucket
                    }
                }
            preferences[AnalyticsStore.dailyBucketsJson] = encodeBuckets(updatedBuckets)
        }
    }

    suspend fun recordCancelledSession(durationMs: Long, recordedOn: LocalDate) {
        dataStore.edit { preferences ->
            preferences[AnalyticsStore.totalCancelledSessions] = (preferences[AnalyticsStore.totalCancelledSessions] ?: 0) + 1
            val updatedBuckets = normalizeBuckets(parseBuckets(preferences[AnalyticsStore.dailyBucketsJson]), todayProvider())
                .map { bucket ->
                    if (bucket.date == recordedOn) {
                        bucket.copy(cancelledSessionCount = bucket.cancelledSessionCount + 1)
                    } else {
                        bucket
                    }
                }
            preferences[AnalyticsStore.dailyBucketsJson] = encodeBuckets(updatedBuckets)
        }
    }

    suspend fun resetAnalytics() {
        dataStore.edit { preferences ->
            preferences.clear()
            preferences[AnalyticsStore.dailyBucketsJson] = encodeBuckets(normalizeBuckets(emptyList(), todayProvider()))
        }
    }

    private fun parseBuckets(json: String?): List<AnalyticsDayBucket> {
        val raw = json?.trim().orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }

        val type = object : TypeToken<List<StoredAnalyticsDayBucket>>() {}.type
        return try {
            gson.fromJson<List<StoredAnalyticsDayBucket>>(raw, type)
                .orEmpty()
                .mapNotNull { storedBucket ->
                    try {
                        AnalyticsDayBucket(
                            date = LocalDate.parse(storedBucket.date),
                            completedSessionCount = storedBucket.completedSessionCount,
                            cancelledSessionCount = storedBucket.cancelledSessionCount,
                            recordingDurationMillis = storedBucket.recordingDurationMillis,
                            rawWordCount = storedBucket.rawWordCount,
                            finalInsertedWordCount = storedBucket.finalInsertedWordCount,
                            insertedCharacterCount = storedBucket.insertedCharacterCount,
                            estimatedTimeSavedMinutes = storedBucket.estimatedTimeSavedMinutes,
                        )
                    } catch (_: DateTimeParseException) {
                        null
                    }
                }
        } catch (_: Exception) {
            emptyList()
        }
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
                )
            }
        )
    }

    private fun normalizeBuckets(
        persisted: List<AnalyticsDayBucket>,
        today: LocalDate,
    ): List<AnalyticsDayBucket> {
        val persistedByDate = persisted.associateBy { it.date }
        return (6L downTo 0L).map { offset ->
            val date = today.minusDays(offset)
            persistedByDate[date] ?: AnalyticsDayBucket(date = date)
        }
    }

    private fun countWords(text: String): Int {
        val boundary = BreakIterator.getWordInstance()
        boundary.setText(text)
        var count = 0
        var start = boundary.first()
        var end = boundary.next()
        while (end != BreakIterator.DONE) {
            val token = text.substring(start, end)
            if (token.any { it.isLetterOrDigit() }) {
                count += 1
            }
            start = end
            end = boundary.next()
        }
        return count
    }

    private fun durationMillisToRoundedMinutes(durationMillis: Long): Int {
        if (durationMillis <= 0L) {
            return 0
        }
        return (durationMillis / 60_000.0).roundToInt()
    }

    private data class StoredAnalyticsDayBucket(
        val date: String,
        val completedSessionCount: Int = 0,
        val cancelledSessionCount: Int = 0,
        val recordingDurationMillis: Long = 0,
        val rawWordCount: Int = 0,
        val finalInsertedWordCount: Int = 0,
        val insertedCharacterCount: Int = 0,
        val estimatedTimeSavedMinutes: Int = 0,
    )
}
