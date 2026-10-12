package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.data.history.HistoryBackupSource
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.temporal.ChronoUnit

class SettingsBackupRepository(
    private val dataStore: DataStore<Preferences>,
    private val credentialSource: ProviderCredentialSource,
    private val credentialSink: ProviderCredentialSink? = null,
    private val gson: Gson = Gson(),
    private val timestampProvider: () -> String = { Instant.now().truncatedTo(ChronoUnit.SECONDS).toString() },
    private val currentAppVersionNameProvider: () -> String = { "" },
    /** Transcript history; null leaves the `history` category out entirely. */
    private val historySource: HistoryBackupSource? = null,
) {
    private val codec = BackupCodec(gson)
    private val payloadBuilder = BackupPayloadBuilder(codec, credentialSource, gson, historySource)
    private val importAnalyzer = BackupImportAnalyzer(currentAppVersionNameProvider)
    private val applier = BackupApplier(
        dataStore = dataStore,
        credentialSink = credentialSink,
        gson = gson,
        historySource = historySource,
        codec = codec,
        repairPlanner = RestoreRepairPlanner(dataStore, credentialSource, codec),
    )

    /** [includeHistory]: add the opt-in `history` category (transcript text, never audio). */
    suspend fun buildExportPayload(includeHistory: Boolean = false): SettingsBackupPayload {
        return payloadBuilder.build(dataStore.data.first(), includeHistory)
    }

    suspend fun exportEncryptedBackup(
        password: String,
        appVersionName: String,
        exportedAtUtc: String = timestampProvider(),
        includeHistory: Boolean = false,
    ): SettingsBackupEnvelope {
        val payloadJson = gson.toJson(buildExportPayload(includeHistory))
        return SettingsBackupCrypto.encryptUtf8(
            plaintext = payloadJson,
            password = password,
            appVersionName = appVersionName,
            exportedAtUtc = exportedAtUtc,
        )
    }

    suspend fun analyzeEncryptedBackup(
        envelope: SettingsBackupEnvelope,
        password: String,
        restoreMode: RestoreMode,
    ): ImportAnalysis {
        val payloadJson = SettingsBackupCrypto.decryptUtf8(envelope, password)
        val currentPayload = buildExportPayload(includeHistory = true)
        val parsed = codec.parseImportPayload(payloadJson)
        val resolvedPayload = BackupMergeResolver.resolve(
            current = currentPayload,
            imported = parsed.payload,
            availableCategoryIds = parsed.availableCategoryIds,
            restoreMode = restoreMode,
        )

        return ImportAnalysis(
            envelopeSchemaVersion = envelope.schemaVersion,
            payloadSchemaVersion = parsed.payloadSchemaVersion,
            backupAppVersionName = envelope.appVersionName,
            exportedAtUtc = envelope.exportedAtUtc,
            restoreMode = restoreMode,
            resolvedPayload = resolvedPayload,
            categoryPreviews = importAnalyzer.buildCategoryPreviews(
                current = currentPayload,
                imported = parsed.payload,
                resolved = resolvedPayload,
                availableCategoryIds = parsed.availableCategoryIds,
                restoreMode = restoreMode,
            ),
            warnings = importAnalyzer.buildWarnings(envelope, parsed.payloadSchemaVersion),
            skippedItems = parsed.skippedItems,
        )
    }

    suspend fun applyImportAnalysis(
        analysis: ImportAnalysis,
        includedCategoryIds: Set<String> = emptySet(),
    ): RestoreSummary {
        return applier.apply(analysis, includedCategoryIds)
    }
}
