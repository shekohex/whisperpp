package com.github.shekohex.whisperpp.analytics

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

const val ANALYTICS_DATASTORE_NAME = "analytics"
const val ANALYTICS_STORE_PATH = "datastore/analytics.preferences_pb"

val Context.analyticsDataStore: DataStore<Preferences> by preferencesDataStore(name = ANALYTICS_DATASTORE_NAME)
