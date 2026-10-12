package com.github.shekohex.whisperpp.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.analytics.AnalyticsRepository
import com.github.shekohex.whisperpp.analytics.analyticsDataStore
import com.github.shekohex.whisperpp.core.network.HttpClientFactory
import com.github.shekohex.whisperpp.core.network.RedactingLoggingInterceptor
import com.github.shekohex.whisperpp.data.BackupStatusStore
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.data.dictionary.DictionaryRepository
import com.github.shekohex.whisperpp.dataStore
import com.github.shekohex.whisperpp.domain.providers.ProviderFactory
import com.github.shekohex.whisperpp.privacy.SecretsStore
import com.github.shekohex.whisperpp.privacy.SendPolicyRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AppContainer"

/**
 * Manual DI: owns process-wide singletons. Created once by [com.github.shekohex.whisperpp.app.WhisperApp].
 */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    val settingsDataStore: DataStore<Preferences> = appContext.dataStore
    val analyticsDataStore: DataStore<Preferences> = appContext.analyticsDataStore

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(settingsDataStore) }
    val analyticsRepository: AnalyticsRepository by lazy { AnalyticsRepository(analyticsDataStore) }
    val secretsStore: SecretsStore by lazy { SecretsStore(appContext) }
    val sendPolicyRepository: SendPolicyRepository by lazy { SendPolicyRepository(settingsDataStore) }
    val dictionaryRepository: DictionaryRepository by lazy { DictionaryRepository(settingsDataStore) }
    val backupStatusStore: BackupStatusStore = BackupStatusStore()

    val httpLoggingInterceptor: RedactingLoggingInterceptor = RedactingLoggingInterceptor()
    val okHttpClient: OkHttpClient by lazy { HttpClientFactory.createOkHttpClient(httpLoggingInterceptor) }
    val updaterHttpClient: HttpClient by lazy { HttpClientFactory.createKtorClient(okHttpClient) }
    val providerFactory: ProviderFactory by lazy { ProviderFactory(okHttpClient, secretsStore::getProviderApiKey) }

    /** Process-lifetime scope for app-level background work (never cancelled). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val migrationsStarted = AtomicBoolean(false)
    private val migrationsDoneState = MutableStateFlow(false)

    private val migrationsFailedState = MutableStateFlow(false)

    /** True once the startup DataStore migrations finished (successfully or not). */
    val migrationsDone: StateFlow<Boolean> = migrationsDoneState.asStateFlow()

    /** True when the last migration run threw; the settings UI offers [retryMigrations]. */
    val migrationsFailed: StateFlow<Boolean> = migrationsFailedState.asStateFlow()

    /** Runs the settings schema migrations once per process, off the main thread. */
    fun startMigrations() {
        if (!migrationsStarted.compareAndSet(false, true)) return
        appScope.launch {
            try {
                settingsRepository.migrateProviderSchemaV2IfNeeded(secretsStore)
                settingsRepository.migrateProviderSchemaV3IfNeeded()
                settingsRepository.migrateProviderSelectionsV2IfNeeded()
                settingsRepository.migratePromptsProfilesSchemaV1IfNeeded()
                settingsRepository.seedPromptLibraryIfNeeded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave the data as is and let the app run; a stuck spinner/keyboard is worse.
                Log.e(TAG, "Startup migrations failed", e)
                migrationsFailedState.value = true
            } finally {
                migrationsDoneState.value = true
            }
        }
    }

    /** Re-runs the migrations after a failure; no-op while a run is in flight or after success. */
    fun retryMigrations() {
        if (!migrationsDoneState.value || !migrationsFailedState.value) return
        migrationsFailedState.value = false
        migrationsDoneState.value = false
        migrationsStarted.set(false)
        startMigrations()
    }

    /** Waits up to [timeoutMs] for [migrationsDone]; returns whether they finished. */
    suspend fun awaitMigrations(timeoutMs: Long): Boolean {
        return withTimeoutOrNull(timeoutMs) { migrationsDone.first { it } } != null
    }
}
