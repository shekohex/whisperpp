package com.github.shekohex.whisperpp.updater

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.github.shekohex.whisperpp.data.PreferenceKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.updateDataStore by preferencesDataStore(name = "updater_state")

class UpdateRepository(private val context: Context) {
    val updateState: Flow<PersistedUpdateState> = context.updateDataStore.data.map { prefs ->
        PersistedUpdateState(
            downloadId = prefs[PreferenceKeys.Updater.DOWNLOAD_ID] ?: -1L,
            expectedSignature = prefs[PreferenceKeys.Updater.EXPECTED_SIGNATURE],
            targetVersion = prefs[PreferenceKeys.Updater.TARGET_VERSION],
            destinationPath = prefs[PreferenceKeys.Updater.DESTINATION_PATH]
        )
    }

    suspend fun saveDownloadState(id: Long, signature: String, version: String, path: String) {
        context.updateDataStore.edit { prefs ->
            prefs[PreferenceKeys.Updater.DOWNLOAD_ID] = id
            prefs[PreferenceKeys.Updater.EXPECTED_SIGNATURE] = signature
            prefs[PreferenceKeys.Updater.TARGET_VERSION] = version
            prefs[PreferenceKeys.Updater.DESTINATION_PATH] = path
        }
    }

    suspend fun clearDownloadState() {
        context.updateDataStore.edit { prefs ->
            prefs.remove(PreferenceKeys.Updater.DOWNLOAD_ID)
            prefs.remove(PreferenceKeys.Updater.EXPECTED_SIGNATURE)
            prefs.remove(PreferenceKeys.Updater.TARGET_VERSION)
            prefs.remove(PreferenceKeys.Updater.DESTINATION_PATH)
        }
    }
}

data class PersistedUpdateState(
    val downloadId: Long,
    val expectedSignature: String?,
    val targetVersion: String?,
    val destinationPath: String?
)
