package com.github.shekohex.whisperpp.analytics

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

const val ANALYTICS_DATASTORE_NAME = "analytics"
const val ANALYTICS_STORE_PATH = "datastore/analytics.preferences_pb"

val Context.analyticsDataStore: DataStore<Preferences> by preferencesDataStore(name = ANALYTICS_DATASTORE_NAME)

internal object AnalyticsStore {
    val totalCompletedSessions = intPreferencesKey("analytics_total_completed_sessions")
    val totalCancelledSessions = intPreferencesKey("analytics_total_cancelled_sessions")
    val totalRecordingDurationMillis = longPreferencesKey("analytics_total_recording_duration_millis")
    val totalRawWordCount = intPreferencesKey("analytics_total_raw_word_count")
    val totalFinalInsertedWordCount = intPreferencesKey("analytics_total_final_inserted_word_count")
    val totalInsertedCharacterCount = intPreferencesKey("analytics_total_inserted_character_count")
    val dailyBucketsJson = stringPreferencesKey("analytics_daily_buckets_json")
}
