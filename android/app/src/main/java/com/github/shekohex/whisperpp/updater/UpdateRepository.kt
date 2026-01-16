package com.github.shekohex.whisperpp.updater

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.updateDataStore by preferencesDataStore(name = "updater_state")

class UpdateRepository(private val context: Context) {
    companion object {
        private val DOWNLOAD_ID = longPreferencesKey("download_id")
        private val EXPECTED_SIGNATURE = stringPreferencesKey("expected_signature")
        private val TARGET_VERSION = stringPreferencesKey("target_version")
        private val DESTINATION_PATH = stringPreferencesKey("destination_path")
    }

    val updateState: Flow<PersistedUpdateState> = context.updateDataStore.data.map { prefs ->
        PersistedUpdateState(
            downloadId = prefs[DOWNLOAD_ID] ?: -1L,
            expectedSignature = prefs[EXPECTED_SIGNATURE],
            targetVersion = prefs[TARGET_VERSION],
            destinationPath = prefs[DESTINATION_PATH]
        )
    }

    suspend fun saveDownloadState(id: Long, signature: String, version: String, path: String) {
        context.updateDataStore.edit { prefs ->
            prefs[DOWNLOAD_ID] = id
            prefs[EXPECTED_SIGNATURE] = signature
            prefs[TARGET_VERSION] = version
            prefs[DESTINATION_PATH] = path
        }
    }

    suspend fun clearDownloadState() {
        context.updateDataStore.edit { prefs ->
            prefs.remove(DOWNLOAD_ID)
            prefs.remove(EXPECTED_SIGNATURE)
            prefs.remove(TARGET_VERSION)
            prefs.remove(DESTINATION_PATH)
        }
    }
}

data class PersistedUpdateState(
    val downloadId: Long,
    val expectedSignature: String?,
    val targetVersion: String?,
    val destinationPath: String?
)
