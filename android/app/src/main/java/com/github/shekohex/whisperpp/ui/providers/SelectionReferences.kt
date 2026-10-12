package com.github.shekohex.whisperpp.ui.providers

import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_PROVIDER_ID

/** Names (string resources) of the active selections that still point at [providerId]. */
internal fun selectionReferencesProvider(providerId: String, prefs: Preferences): List<Int> = buildList {
    if (prefs[ACTIVE_STT_PROVIDER_ID] == providerId) add(R.string.provider_selection_ref_dictation)
    if (prefs[ACTIVE_TEXT_PROVIDER_ID] == providerId) add(R.string.provider_selection_ref_enhancement)
    if (prefs[COMMAND_TEXT_PROVIDER_ID] == providerId) add(R.string.provider_selection_ref_command)
}

/** Names (string resources) of the active selections that use [modelId] of [providerId]. */
internal fun selectionReferencesModel(providerId: String, modelId: String, prefs: Preferences): List<Int> = buildList {
    if (prefs[ACTIVE_STT_PROVIDER_ID] == providerId && prefs[ACTIVE_STT_MODEL_ID] == modelId) {
        add(R.string.provider_selection_ref_dictation)
    }
    if (prefs[ACTIVE_TEXT_PROVIDER_ID] == providerId && prefs[ACTIVE_TEXT_MODEL_ID] == modelId) {
        add(R.string.provider_selection_ref_enhancement)
    }
    if (prefs[COMMAND_TEXT_PROVIDER_ID] == providerId && prefs[COMMAND_TEXT_MODEL_ID] == modelId) {
        add(R.string.provider_selection_ref_command)
    }
}
