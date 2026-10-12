package com.github.shekohex.whisperpp.data.autolearn

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

const val AUTO_LEARN_DATASTORE_NAME = "autolearn_queue"

/** Auto-Learn pending candidates and review proposals; kept out of the settings DataStore and backups. */
val Context.autoLearnDataStore: DataStore<Preferences> by preferencesDataStore(name = AUTO_LEARN_DATASTORE_NAME)
