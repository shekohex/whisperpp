package com.github.shekohex.whisperpp.data

import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_PROVIDER_ID

internal data class ProviderSelection(
    val providerId: String,
    val modelId: String,
)

internal data class EffectiveSelections(
    val stt: ProviderSelection,
    val text: ProviderSelection,
    val commandText: ProviderSelection,
)

internal data class SelectionValidationResult(
    val isSttValid: Boolean,
    val isTextValid: Boolean,
    val isCommandOverrideValid: Boolean,
    val keysToClear: Set<PreferenceKey<String>>,
    val effective: EffectiveSelections,
)

internal fun validateSelections(
    providers: List<ServiceProvider>,
    sttProviderId: String,
    sttModelId: String,
    textProviderId: String,
    textModelId: String,
    commandProviderId: String,
    commandModelId: String,
): SelectionValidationResult {
    val sttProvider = providers.find { it.id == sttProviderId }
    val textProvider = providers.find { it.id == textProviderId }
    val commandProvider = providers.find { it.id == commandProviderId }

    val sttProviderValid = sttProviderId.isNotBlank() && sttProvider != null
    val sttModelValid = sttModelId.isNotBlank() && sttProvider?.models?.any { it.id == sttModelId } == true

    val textProviderValid = textProviderId.isNotBlank() && textProvider != null
    val textModelValid = textModelId.isNotBlank() && textProvider?.models?.any { it.id == textModelId } == true

    val commandProviderValid = commandProviderId.isNotBlank() && commandProvider != null
    val commandModelValid = commandModelId.isNotBlank() && commandProvider?.models?.any { it.id == commandModelId } == true

    val sttValid = sttProviderValid && sttModelValid
    val textValid = textProviderValid && textModelValid
    val commandOverridePresent = commandProviderId.isNotBlank() || commandModelId.isNotBlank()
    val commandOverrideValid = if (!commandOverridePresent) {
        true
    } else {
        commandProviderValid && commandModelValid
    }

    val keysToClear = buildSet<PreferenceKey<String>> {
        if (!sttProviderValid && sttProviderId.isNotBlank()) {
            add(ACTIVE_STT_PROVIDER_ID)
            add(ACTIVE_STT_MODEL_ID)
        } else if (!sttModelValid && sttModelId.isNotBlank()) {
            add(ACTIVE_STT_MODEL_ID)
        }

        if (!textProviderValid && textProviderId.isNotBlank()) {
            add(ACTIVE_TEXT_PROVIDER_ID)
            add(ACTIVE_TEXT_MODEL_ID)
        } else if (!textModelValid && textModelId.isNotBlank()) {
            add(ACTIVE_TEXT_MODEL_ID)
        }

        if (!commandOverrideValid && commandOverridePresent) {
            add(COMMAND_TEXT_PROVIDER_ID)
            add(COMMAND_TEXT_MODEL_ID)
        }
    }

    val effectiveStt = ProviderSelection(
        providerId = if (sttProviderValid) sttProviderId else "",
        modelId = if (sttProviderValid && sttModelValid) sttModelId else "",
    )
    val effectiveText = ProviderSelection(
        providerId = if (textProviderValid) textProviderId else "",
        modelId = if (textProviderValid && textModelValid) textModelId else "",
    )
    val effectiveCommand = if (commandOverridePresent && commandOverrideValid) {
        ProviderSelection(providerId = commandProviderId, modelId = commandModelId)
    } else {
        effectiveText
    }

    return SelectionValidationResult(
        isSttValid = sttValid,
        isTextValid = textValid,
        isCommandOverrideValid = commandOverrideValid,
        keysToClear = keysToClear,
        effective = EffectiveSelections(stt = effectiveStt, text = effectiveText, commandText = effectiveCommand),
    )
}
