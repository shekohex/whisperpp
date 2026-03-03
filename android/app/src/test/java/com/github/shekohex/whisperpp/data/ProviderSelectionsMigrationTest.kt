package com.github.shekohex.whisperpp.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.github.shekohex.whisperpp.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.COMMAND_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.MODEL
import com.github.shekohex.whisperpp.SMART_FIX_BACKEND
import com.github.shekohex.whisperpp.SMART_FIX_MODEL
import com.github.shekohex.whisperpp.SPEECH_TO_TEXT_BACKEND
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSelectionsMigrationTest {

    @Test
    fun migrateLegacySelectionsToV2_copiesLegacyKeysWhenNewKeysUnset() {
        val prefs = mutablePreferencesOf(
            SPEECH_TO_TEXT_BACKEND to "sttProvider",
            MODEL to "sttModel",
            SMART_FIX_BACKEND to "textProvider",
            SMART_FIX_MODEL to "textModel",
        )

        migrateLegacySelectionsToV2(prefs)

        assertEquals("sttProvider", prefs[ACTIVE_STT_PROVIDER_ID])
        assertEquals("sttModel", prefs[ACTIVE_STT_MODEL_ID])
        assertEquals("textProvider", prefs[ACTIVE_TEXT_PROVIDER_ID])
        assertEquals("textModel", prefs[ACTIVE_TEXT_MODEL_ID])
        assertNull(prefs[COMMAND_TEXT_PROVIDER_ID])
        assertNull(prefs[COMMAND_TEXT_MODEL_ID])
    }

    @Test
    fun migrateLegacySelectionsToV2_noOpWhenNewKeysAlreadyExist() {
        val prefs = mutablePreferencesOf(
            ACTIVE_STT_PROVIDER_ID to "already",
            ACTIVE_STT_MODEL_ID to "alreadyModel",
            SPEECH_TO_TEXT_BACKEND to "legacySttProvider",
            MODEL to "legacySttModel",
            SMART_FIX_BACKEND to "legacyTextProvider",
            SMART_FIX_MODEL to "legacyTextModel",
        )

        migrateLegacySelectionsToV2(prefs)

        assertEquals("already", prefs[ACTIVE_STT_PROVIDER_ID])
        assertEquals("alreadyModel", prefs[ACTIVE_STT_MODEL_ID])
        assertNull(prefs[ACTIVE_TEXT_PROVIDER_ID])
        assertNull(prefs[ACTIVE_TEXT_MODEL_ID])
    }

    @Test
    fun validateSelections_selectedProviderMissing_clearsProviderAndModel() {
        val providers = listOf(
            ServiceProvider(
                id = "p1",
                name = "P1",
                type = ProviderType.CUSTOM,
                endpoint = "https://example.com/v1",
                models = listOf(ModelConfig(id = "m1", name = "M1", kind = ModelKind.STT)),
            )
        )

        val result = validateSelections(
            providers = providers,
            sttProviderId = "missing",
            sttModelId = "m1",
            textProviderId = "",
            textModelId = "",
            commandProviderId = "",
            commandModelId = "",
        )

        assertFalse(result.isSttValid)
        assertTrue(result.keysToClear.contains(ACTIVE_STT_PROVIDER_ID))
        assertTrue(result.keysToClear.contains(ACTIVE_STT_MODEL_ID))
    }

    @Test
    fun validateSelections_selectedModelMissingUnderProvider_clearsModelOnly() {
        val providers = listOf(
            ServiceProvider(
                id = "p1",
                name = "P1",
                type = ProviderType.CUSTOM,
                endpoint = "https://example.com/v1",
                models = listOf(ModelConfig(id = "m1", name = "M1", kind = ModelKind.TEXT)),
            )
        )

        val result = validateSelections(
            providers = providers,
            sttProviderId = "",
            sttModelId = "",
            textProviderId = "p1",
            textModelId = "missing",
            commandProviderId = "",
            commandModelId = "",
        )

        assertFalse(result.isTextValid)
        assertFalse(result.keysToClear.contains(ACTIVE_TEXT_PROVIDER_ID))
        assertTrue(result.keysToClear.contains(ACTIVE_TEXT_MODEL_ID))
    }

    @Test
    fun validateSelections_commandOverrideInvalid_clearsOverrideAndInheritsSharedText() {
        val providers = listOf(
            ServiceProvider(
                id = "p1",
                name = "P1",
                type = ProviderType.CUSTOM,
                endpoint = "https://example.com/v1",
                models = listOf(ModelConfig(id = "m1", name = "M1", kind = ModelKind.TEXT)),
            )
        )

        val result = validateSelections(
            providers = providers,
            sttProviderId = "",
            sttModelId = "",
            textProviderId = "p1",
            textModelId = "m1",
            commandProviderId = "p1",
            commandModelId = "missing",
        )

        assertTrue(result.isTextValid)
        assertFalse(result.isCommandOverrideValid)
        assertTrue(result.keysToClear.contains(COMMAND_TEXT_PROVIDER_ID))
        assertTrue(result.keysToClear.contains(COMMAND_TEXT_MODEL_ID))
        assertEquals("p1", result.effective.commandText.providerId)
        assertEquals("m1", result.effective.commandText.modelId)
    }
}
