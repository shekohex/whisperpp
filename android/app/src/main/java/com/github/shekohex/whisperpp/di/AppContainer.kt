package com.github.shekohex.whisperpp.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.analytics.AnalyticsRepository
import com.github.shekohex.whisperpp.analytics.analyticsDataStore
import com.github.shekohex.whisperpp.core.network.HttpClientFactory
import com.github.shekohex.whisperpp.core.network.RedactingLoggingInterceptor
import com.github.shekohex.whisperpp.core.permissions.AndroidSetupSystemChecks
import com.github.shekohex.whisperpp.data.BackupStatusStore
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnDictionaryEditor
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnModelResolver
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnPendingQueue
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnProposalStore
import com.github.shekohex.whisperpp.data.autolearn.autoLearnDataStore
import com.github.shekohex.whisperpp.data.dictionary.DictionaryRepository
import com.github.shekohex.whisperpp.data.modes.LegacyModesMigration
import com.github.shekohex.whisperpp.data.modes.ModesRepository
import com.github.shekohex.whisperpp.data.history.DictationHistoryRecorder
import com.github.shekohex.whisperpp.data.history.HistoryAudioStore
import com.github.shekohex.whisperpp.data.history.HistoryMaintenance
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryDatabase
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryRepository
import com.github.shekohex.whisperpp.data.dictionary.readDictionary
import com.github.shekohex.whisperpp.data.dictionary.writeDictionary
import com.github.shekohex.whisperpp.dataStore
import com.github.shekohex.whisperpp.domain.setup.SetupStatusChecker
import com.github.shekohex.whisperpp.domain.dictation.HistoryRecorder
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnAiReviewer
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnCoordinator
import com.github.shekohex.whisperpp.domain.providers.ProviderFactory
import com.github.shekohex.whisperpp.privacy.SecretsStore
import com.github.shekohex.whisperpp.privacy.SendPolicyRepository
import com.google.gson.Gson
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
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AppContainer"
private const val AUTO_LEARN_TAG = "AutoLearn"

/**
 * Manual DI: owns process-wide singletons. Created once by [com.github.shekohex.whisperpp.app.WhisperApp].
 */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    /** Wall clock at process start (the container is created in `Application.onCreate`). */
    private val createdAt: Long = System.currentTimeMillis()

    val settingsDataStore: DataStore<Preferences> = appContext.dataStore
    val analyticsDataStore: DataStore<Preferences> = appContext.analyticsDataStore

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(settingsDataStore) }
    val analyticsRepository: AnalyticsRepository by lazy { AnalyticsRepository(analyticsDataStore) }
    val secretsStore: SecretsStore by lazy { SecretsStore(appContext) }
    val sendPolicyRepository: SendPolicyRepository by lazy { SendPolicyRepository(settingsDataStore) }
    val dictionaryRepository: DictionaryRepository by lazy { DictionaryRepository(settingsDataStore) }
    val modesRepository: ModesRepository by lazy { ModesRepository(settingsDataStore) }

    /** One-way legacy → modes migration; the Modes UI previews it and asks for a backup first. */
    val legacyModesMigration: LegacyModesMigration by lazy { LegacyModesMigration(settingsDataStore) }
    val backupStatusStore: BackupStatusStore = BackupStatusStore()
    val setupStatusChecker: SetupStatusChecker by lazy {
        SetupStatusChecker(
            systemChecks = AndroidSetupSystemChecks(appContext),
            providers = settingsRepository.providers,
            preferences = settingsDataStore.data,
            hasCredential = { id -> !secretsStore.getProviderApiKey(id).isNullOrBlank() },
        )
    }

    // Transcript history (§4.8).
    private val historyDatabase: TranscriptHistoryDatabase by lazy { TranscriptHistoryDatabase.create(appContext) }
    val historyAudioStore: HistoryAudioStore by lazy {
        HistoryAudioStore(File(appContext.filesDir, HistoryAudioStore.DIR_NAME))
    }
    val transcriptHistoryRepository: TranscriptHistoryRepository by lazy {
        TranscriptHistoryRepository(historyDatabase.dao(), historyAudioStore)
    }
    val historyRecorder: HistoryRecorder by lazy {
        DictationHistoryRecorder(transcriptHistoryRepository, historyAudioStore, settingsRepository.historySettings, appScope)
    }
    private val historyMaintenance: HistoryMaintenance by lazy {
        HistoryMaintenance(
            repository = transcriptHistoryRepository,
            settings = settingsRepository.historySettings,
            lastRunAt = settingsRepository::historyLastPruneAt,
            markRun = settingsRepository::setHistoryLastPruneAt,
            processStartedAt = createdAt,
        )
    }

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
                settingsRepository.migrateOnboardingGateIfNeeded(AndroidSetupSystemChecks(appContext)::isImeEnabled)
                settingsRepository.migrateProviderSchemaV2IfNeeded(secretsStore)
                settingsRepository.migrateProviderSchemaV3IfNeeded()
                settingsRepository.migrateProviderSelectionsV2IfNeeded()
                settingsRepository.migratePromptsProfilesSchemaV1IfNeeded()
                settingsRepository.seedPromptLibraryIfNeeded()
                legacyModesMigration.runIfNothingToMigrate()
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

    /** App start: analytics schema step, then history recovery and retention pruning, off the main thread. */
    fun startDataMaintenance() {
        appScope.launch {
            guardedMaintenance { analyticsRepository.migrateIfNeeded() }
            guardedMaintenance { historyMaintenance.runAtStartup() }
        }
    }

    /** The keyboard service calls this when it starts: retention pruning at most once a day. */
    fun pruneHistoryIfDue() {
        appScope.launch { guardedMaintenance { historyMaintenance.runIfDue() } }
    }

    private suspend fun guardedMaintenance(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Data maintenance failed: ${e.javaClass.simpleName}")
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

    // Auto-Learn (§4.11), off by default; the IME capture and review UI call [autoLearnCoordinator].
    val autoLearnDataStore: DataStore<Preferences> = appContext.autoLearnDataStore
    val autoLearnQueue: AutoLearnPendingQueue by lazy { AutoLearnPendingQueue(autoLearnDataStore) }
    val autoLearnProposalStore: AutoLearnProposalStore by lazy {
        val gson = Gson()
        AutoLearnProposalStore(
            autoLearnDataStore,
            AutoLearnDictionaryEditor { transform ->
                settingsDataStore.edit { prefs -> writeDictionary(prefs, transform(readDictionary(prefs, gson)), gson) }
            },
        )
    }
    private val autoLearnPrompt: String by lazy {
        appContext.resources.openRawResource(R.raw.autolearn_review_prompt).bufferedReader().use { it.readText() }
    }
    val autoLearnReviewer: AutoLearnAiReviewer by lazy {
        AutoLearnAiReviewer(
            prompt = { autoLearnPrompt },
            resolveModel = {
                AutoLearnModelResolver.resolve(
                    settingsRepository.autoLearnSettings.first(),
                    settingsRepository.providers.first(),
                    providerFactory,
                )
            },
            log = { Log.i(AUTO_LEARN_TAG, it) },
        )
    }
    val autoLearnCoordinator: AutoLearnCoordinator by lazy {
        AutoLearnCoordinator(
            settings = settingsRepository.autoLearnSettings,
            markReviewed = settingsRepository::setAutoLearnLastReviewAt,
            queue = autoLearnQueue,
            reviewer = autoLearnReviewer,
            proposalStore = autoLearnProposalStore,
            scope = appScope,
            log = { Log.i(AUTO_LEARN_TAG, it) },
        )
    }
}
