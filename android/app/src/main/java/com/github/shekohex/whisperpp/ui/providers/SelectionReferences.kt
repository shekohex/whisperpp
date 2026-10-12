package com.github.shekohex.whisperpp.ui.providers

import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_PROVIDER_ID

/** Names of the active selections that still point at [providerId]; deleting it would break them. */
internal fun selectionReferencesProvider(providerId: String, prefs: Preferences): List<String> = buildList {
    if (prefs[ACTIVE_STT_PROVIDER_ID] == providerId) add("Dictation (STT)")
    if (prefs[ACTIVE_TEXT_PROVIDER_ID] == providerId) add("Enhancement (Text)")
    if (prefs[COMMAND_TEXT_PROVIDER_ID] == providerId) add("Command override")
}

/** Names of the active selections that use [modelId] of [providerId]. */
internal fun selectionReferencesModel(providerId: String, modelId: String, prefs: Preferences): List<String> = buildList {
    if (prefs[ACTIVE_STT_PROVIDER_ID] == providerId && prefs[ACTIVE_STT_MODEL_ID] == modelId) add("Dictation (STT)")
    if (prefs[ACTIVE_TEXT_PROVIDER_ID] == providerId && prefs[ACTIVE_TEXT_MODEL_ID] == modelId) add("Enhancement (Text)")
    if (prefs[COMMAND_TEXT_PROVIDER_ID] == providerId && prefs[COMMAND_TEXT_MODEL_ID] == modelId) add("Command override")
}
