package com.github.shekohex.whisperpp.updater

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.github.shekohex.whisperpp.data.PreferenceKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.updateDataStore by preferencesDataStore(name = "updater_state")

class UpdateRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.updateDataStore)

    val updateState: Flow<PersistedUpdateState> = store.data.map { prefs ->
        PersistedUpdateState(
            downloadId = prefs[PreferenceKeys.Updater.DOWNLOAD_ID] ?: -1L,
            expectedSignature = prefs[PreferenceKeys.Updater.EXPECTED_SIGNATURE],
            targetVersion = prefs[PreferenceKeys.Updater.TARGET_VERSION],
            destinationPath = prefs[PreferenceKeys.Updater.DESTINATION_PATH],
            targetVersionCode = prefs[PreferenceKeys.Updater.TARGET_VERSION_CODE],
        )
    }

    val installState: Flow<PersistedInstallState?> = store.data.map { prefs ->
        val sessionId = prefs[PreferenceKeys.Updater.INSTALL_SESSION_ID]
        val versionCode = prefs[PreferenceKeys.Updater.INSTALL_VERSION_CODE]
        if (sessionId == null || versionCode == null) null else PersistedInstallState(
            sessionId, versionCode, prefs[PreferenceKeys.Updater.INSTALL_PENDING_USER_ACTION] ?: false,
        )
    }

    suspend fun saveInstallSession(sessionId: Int, versionCode: Int, pendingUserAction: Boolean = false) {
        store.edit { prefs ->
            prefs[PreferenceKeys.Updater.INSTALL_SESSION_ID] = sessionId
            prefs[PreferenceKeys.Updater.INSTALL_VERSION_CODE] = versionCode
            prefs[PreferenceKeys.Updater.INSTALL_PENDING_USER_ACTION] = pendingUserAction
        }
    }

    suspend fun clearInstallSession() {
        store.edit { prefs ->
            prefs.remove(PreferenceKeys.Updater.INSTALL_SESSION_ID)
            prefs.remove(PreferenceKeys.Updater.INSTALL_VERSION_CODE)
            prefs.remove(PreferenceKeys.Updater.INSTALL_PENDING_USER_ACTION)
        }
    }

    /** Last permanent update-check failure recorded by the background worker, if any. */
    val lastCheckError: Flow<String?> = store.data.map { prefs ->
        prefs[PreferenceKeys.Updater.LAST_CHECK_ERROR]
    }

    suspend fun saveCheckError(message: String?) {
        store.edit { prefs ->
            if (message == null) {
                prefs.remove(PreferenceKeys.Updater.LAST_CHECK_ERROR)
            } else {
                prefs[PreferenceKeys.Updater.LAST_CHECK_ERROR] = message
            }
        }
    }

    suspend fun saveDownloadState(id: Long, signature: String, version: String, path: String, versionCode: Int? = null) {
        store.edit { prefs ->
            prefs[PreferenceKeys.Updater.DOWNLOAD_ID] = id
            prefs[PreferenceKeys.Updater.EXPECTED_SIGNATURE] = signature
            prefs[PreferenceKeys.Updater.TARGET_VERSION] = version
            prefs[PreferenceKeys.Updater.DESTINATION_PATH] = path
            if (versionCode == null) prefs.remove(PreferenceKeys.Updater.TARGET_VERSION_CODE)
            else prefs[PreferenceKeys.Updater.TARGET_VERSION_CODE] = versionCode
        }
    }

    suspend fun clearDownloadState(downloadId: Long? = null) {
        store.edit { prefs ->
            if (downloadId != null && prefs[PreferenceKeys.Updater.DOWNLOAD_ID] != downloadId) return@edit
            prefs.remove(PreferenceKeys.Updater.DOWNLOAD_ID)
            prefs.remove(PreferenceKeys.Updater.EXPECTED_SIGNATURE)
            prefs.remove(PreferenceKeys.Updater.TARGET_VERSION)
            prefs.remove(PreferenceKeys.Updater.DESTINATION_PATH)
            prefs.remove(PreferenceKeys.Updater.TARGET_VERSION_CODE)
        }
    }
}

data class PersistedUpdateState(
    val downloadId: Long,
    val expectedSignature: String?,
    val targetVersion: String?,
    val destinationPath: String?,
    val targetVersionCode: Int? = null,
) {
    fun matches(update: UpdateCheckResult.Available, installedVersionCode: Int): Boolean =
        downloadId != -1L && !destinationPath.isNullOrBlank() &&
            update.release.versionCode > installedVersionCode &&
            targetVersionCode == update.release.versionCode && targetVersion == update.release.version &&
            expectedSignature == update.platformInfo.signature
}

data class PersistedInstallState(val sessionId: Int, val versionCode: Int, val pendingUserAction: Boolean = false)
