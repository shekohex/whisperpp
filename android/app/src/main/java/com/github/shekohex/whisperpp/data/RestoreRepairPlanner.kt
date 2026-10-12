package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

internal data class RestoreRepairOutcome(
    val clearedSelections: List<ClearedSelection>,
    val repairEntries: List<RestoreRepairEntry>,
)

private data class SelectionSnapshot(
    val type: RestoreSelectionType,
    val providerKey: PreferenceKey<String>,
    val modelKey: PreferenceKey<String>,
    val providerId: String,
    val modelId: String,
)

private data class SelectionRepairMutation(
    val selection: ClearedSelection,
    val repairEntry: RestoreRepairEntry,
)

/** After a restore, clears active selections that no longer resolve and builds the repair checklist. */
internal class RestoreRepairPlanner(
    private val dataStore: DataStore<Preferences>,
    private val credentialSource: ProviderCredentialSource,
    private val codec: BackupCodec,
) {
    suspend fun repairSelections(): RestoreRepairOutcome {
        val clearedSelections = mutableListOf<ClearedSelection>()
        val repairEntries = mutableListOf<RestoreRepairEntry>()

        val prefsAfterApply = dataStore.data.first()
        val providersAfterApply = codec.parseProviders(prefsAfterApply[PreferenceKeys.PROVIDERS_JSON])
        val selectionValidation = validateSelections(
            providers = providersAfterApply,
            sttProviderId = prefsAfterApply.stringValue(PreferenceKeys.ACTIVE_STT_PROVIDER_ID),
            sttModelId = prefsAfterApply.stringValue(PreferenceKeys.ACTIVE_STT_MODEL_ID),
            textProviderId = prefsAfterApply.stringValue(PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID),
            textModelId = prefsAfterApply.stringValue(PreferenceKeys.ACTIVE_TEXT_MODEL_ID),
            commandProviderId = prefsAfterApply.stringValue(PreferenceKeys.COMMAND_TEXT_PROVIDER_ID),
            commandModelId = prefsAfterApply.stringValue(PreferenceKeys.COMMAND_TEXT_MODEL_ID),
        )
        if (selectionValidation.keysToClear.isNotEmpty()) {
            val validationClears = buildSelectionValidationRepairs(
                prefs = prefsAfterApply,
                providers = providersAfterApply,
                validation = selectionValidation,
            )
            dataStore.edit { prefs ->
                selectionValidation.keysToClear.forEach { prefs.remove(it) }
            }
            clearedSelections += validationClears.map { it.selection }
            repairEntries += validationClears.map { it.repairEntry }
        }

        val prefsAfterValidation = dataStore.data.first()
        val missingCredentialProviders = providersAfterApply
            .filter { it.authMode == ProviderAuthMode.API_KEY }
            .filter { credentialSource.getProviderApiKey(it.id).isNullOrBlank() }
        if (missingCredentialProviders.isNotEmpty()) {
            repairEntries += missingCredentialProviders.map { provider ->
                RestoreRepairEntry(
                    area = RestoreRepairArea.PROVIDER_CREDENTIALS,
                    providerId = provider.id,
                    providerName = provider.name,
                    message = "Re-enter the API key for ${provider.name} before using it.",
                )
            }

            val credentialClears = buildCredentialRepairMutations(
                prefs = prefsAfterValidation,
                providers = providersAfterApply,
                missingCredentialProviders = missingCredentialProviders,
            )
            if (credentialClears.isNotEmpty()) {
                dataStore.edit { prefs ->
                    credentialClears.forEach { mutation ->
                        prefs.remove(selectionProviderKey(mutation.selection.selectionType))
                        prefs.remove(selectionModelKey(mutation.selection.selectionType))
                    }
                }
                clearedSelections += credentialClears.map { it.selection }
                repairEntries += credentialClears.map { it.repairEntry }
            }
        }

        return RestoreRepairOutcome(clearedSelections, repairEntries)
    }

    private fun buildSelectionValidationRepairs(
        prefs: Preferences,
        providers: List<ServiceProvider>,
        validation: SelectionValidationResult,
    ): List<SelectionRepairMutation> {
        return selectionSnapshots(prefs)
            .filter { snapshot -> snapshot.providerKey in validation.keysToClear || snapshot.modelKey in validation.keysToClear }
            .map { snapshot ->
                val provider = providers.find { it.id == snapshot.providerId }
                val reason = when {
                    provider == null || snapshot.providerId.isBlank() -> "The restored ${selectionLabel(snapshot.type)} provider is unavailable on this device."
                    provider.models.none { it.id == snapshot.modelId } || snapshot.modelId.isBlank() -> "The restored ${selectionLabel(snapshot.type)} model is unavailable for ${provider.name}."
                    else -> "The restored ${selectionLabel(snapshot.type)} selection is no longer valid."
                }
                SelectionRepairMutation(
                    selection = ClearedSelection(
                        selectionType = snapshot.type,
                        providerId = snapshot.providerId,
                        modelId = snapshot.modelId,
                        reason = reason,
                    ),
                    repairEntry = RestoreRepairEntry(
                        area = selectionArea(snapshot.type),
                        providerId = snapshot.providerId.ifBlank { null },
                        providerName = provider?.name,
                        selectionType = snapshot.type,
                        message = "Choose a new ${selectionLabel(snapshot.type)} provider/model in settings.",
                    ),
                )
            }
    }

    private fun buildCredentialRepairMutations(
        prefs: Preferences,
        providers: List<ServiceProvider>,
        missingCredentialProviders: List<ServiceProvider>,
    ): List<SelectionRepairMutation> {
        val missingProviderIds = missingCredentialProviders.map { it.id }.toSet()
        return selectionSnapshots(prefs)
            .filter { it.providerId.isNotBlank() && it.modelId.isNotBlank() }
            .filter { it.providerId in missingProviderIds }
            .map { snapshot ->
                val provider = providers.find { it.id == snapshot.providerId }
                val providerName = provider?.name ?: snapshot.providerId
                SelectionRepairMutation(
                    selection = ClearedSelection(
                        selectionType = snapshot.type,
                        providerId = snapshot.providerId,
                        modelId = snapshot.modelId,
                        reason = "Missing restored API key for $providerName.",
                    ),
                    repairEntry = RestoreRepairEntry(
                        area = selectionArea(snapshot.type),
                        providerId = snapshot.providerId,
                        providerName = provider?.name,
                        selectionType = snapshot.type,
                        message = "Restore the API key for $providerName and then reselect it for ${selectionLabel(snapshot.type)}.",
                    ),
                )
            }
    }

    private fun selectionSnapshots(prefs: Preferences): List<SelectionSnapshot> {
        return listOf(
            SelectionSnapshot(
                type = RestoreSelectionType.ACTIVE_STT,
                providerKey = PreferenceKeys.ACTIVE_STT_PROVIDER_ID,
                modelKey = PreferenceKeys.ACTIVE_STT_MODEL_ID,
                providerId = prefs.stringValue(PreferenceKeys.ACTIVE_STT_PROVIDER_ID),
                modelId = prefs.stringValue(PreferenceKeys.ACTIVE_STT_MODEL_ID),
            ),
            SelectionSnapshot(
                type = RestoreSelectionType.ACTIVE_TEXT,
                providerKey = PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID,
                modelKey = PreferenceKeys.ACTIVE_TEXT_MODEL_ID,
                providerId = prefs.stringValue(PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID),
                modelId = prefs.stringValue(PreferenceKeys.ACTIVE_TEXT_MODEL_ID),
            ),
            SelectionSnapshot(
                type = RestoreSelectionType.COMMAND_TEXT,
                providerKey = PreferenceKeys.COMMAND_TEXT_PROVIDER_ID,
                modelKey = PreferenceKeys.COMMAND_TEXT_MODEL_ID,
                providerId = prefs.stringValue(PreferenceKeys.COMMAND_TEXT_PROVIDER_ID),
                modelId = prefs.stringValue(PreferenceKeys.COMMAND_TEXT_MODEL_ID),
            ),
        )
    }

    private fun selectionArea(type: RestoreSelectionType): RestoreRepairArea {
        return when (type) {
            RestoreSelectionType.ACTIVE_STT -> RestoreRepairArea.ACTIVE_STT
            RestoreSelectionType.ACTIVE_TEXT -> RestoreRepairArea.ACTIVE_TEXT
            RestoreSelectionType.COMMAND_TEXT -> RestoreRepairArea.COMMAND_TEXT
        }
    }

    private fun selectionLabel(type: RestoreSelectionType): String {
        return when (type) {
            RestoreSelectionType.ACTIVE_STT -> "dictation"
            RestoreSelectionType.ACTIVE_TEXT -> "enhancement"
            RestoreSelectionType.COMMAND_TEXT -> "command mode"
        }
    }

    private fun selectionProviderKey(type: RestoreSelectionType): PreferenceKey<String> {
        return when (type) {
            RestoreSelectionType.ACTIVE_STT -> PreferenceKeys.ACTIVE_STT_PROVIDER_ID
            RestoreSelectionType.ACTIVE_TEXT -> PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID
            RestoreSelectionType.COMMAND_TEXT -> PreferenceKeys.COMMAND_TEXT_PROVIDER_ID
        }
    }

    private fun selectionModelKey(type: RestoreSelectionType): PreferenceKey<String> {
        return when (type) {
            RestoreSelectionType.ACTIVE_STT -> PreferenceKeys.ACTIVE_STT_MODEL_ID
            RestoreSelectionType.ACTIVE_TEXT -> PreferenceKeys.ACTIVE_TEXT_MODEL_ID
            RestoreSelectionType.COMMAND_TEXT -> PreferenceKeys.COMMAND_TEXT_MODEL_ID
        }
    }
}
