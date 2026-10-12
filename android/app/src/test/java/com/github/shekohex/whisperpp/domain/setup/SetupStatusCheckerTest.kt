package com.github.shekohex.whisperpp.domain.setup

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.PreferenceKeys
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupStatusCheckerTest {

    private class FakeSystemChecks(
        var imeEnabled: Boolean = false,
        var imeSelected: Boolean = false,
        var micGranted: Boolean = false,
        var notifications: Boolean = false,
    ) : SetupSystemChecks {
        val changeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        override fun isImeEnabled() = imeEnabled
        override fun isImeSelected() = imeSelected
        override fun isMicGranted() = micGranted
        override fun areNotificationsGranted() = notifications
        override val changes: Flow<Unit> = changeEvents
    }

    private val provider = ServiceProvider(
        id = "openai",
        name = "OpenAI",
        type = ProviderType.OPENAI,
        endpoint = "https://api.openai.com/v1",
        models = listOf(
            ModelConfig(id = "whisper-1", name = "Whisper", capabilities = ModelCapabilities(stt = true)),
            ModelConfig(id = "gpt-4o-mini", name = "GPT", capabilities = ModelCapabilities(text = true)),
        ),
    )

    private val sttSelected = preferencesOf(
        PreferenceKeys.ACTIVE_STT_PROVIDER_ID to "openai",
        PreferenceKeys.ACTIVE_STT_MODEL_ID to "whisper-1",
    )

    private fun checker(
        system: SetupSystemChecks = FakeSystemChecks(),
        providers: List<ServiceProvider> = listOf(provider),
        prefs: Preferences = sttSelected,
        keys: Set<String> = setOf("openai"),
    ) = SetupStatusChecker(
        systemChecks = system,
        providers = MutableStateFlow(providers),
        preferences = MutableStateFlow(prefs),
        hasCredential = { it in keys },
    )

    @Test
    fun compute_reportsSystemChecks() {
        val system = FakeSystemChecks(imeEnabled = true, imeSelected = false, micGranted = true, notifications = true)

        val status = checker(system = system).compute(listOf(provider), sttSelected)

        assertTrue(status.imeEnabled)
        assertFalse(status.imeSelected)
        assertTrue(status.micGranted)
        assertTrue(status.notificationsGranted)
    }

    @Test
    fun compute_sttConfiguredNeedsSelectionAndCredential() {
        assertTrue(checker().compute(listOf(provider), sttSelected).sttProviderConfigured)
        assertFalse(checker(keys = emptySet()).compute(listOf(provider), sttSelected).sttProviderConfigured)
        assertFalse(checker().compute(listOf(provider), preferencesOf()).sttProviderConfigured)
        assertFalse(checker().compute(emptyList(), sttSelected).sttProviderConfigured)
    }

    @Test
    fun compute_staleModelIsNotConfigured() {
        val stale = preferencesOf(
            PreferenceKeys.ACTIVE_STT_PROVIDER_ID to "openai",
            PreferenceKeys.ACTIVE_STT_MODEL_ID to "removed-model",
        )

        assertFalse(checker().compute(listOf(provider), stale).sttProviderConfigured)
    }

    @Test
    fun compute_noAuthProviderNeedsNoCredential() {
        val local = provider.copy(id = "asr", authMode = ProviderAuthMode.NO_AUTH)
        val prefs = preferencesOf(
            PreferenceKeys.ACTIVE_STT_PROVIDER_ID to "asr",
            PreferenceKeys.ACTIVE_STT_MODEL_ID to "whisper-1",
        )

        assertTrue(checker(keys = emptySet()).compute(listOf(local), prefs).sttProviderConfigured)
    }

    @Test
    fun compute_enhancementIsOptional() {
        val system = FakeSystemChecks(imeEnabled = true, imeSelected = true, micGranted = true)
        val status = checker(system = system).compute(listOf(provider), sttSelected)

        assertFalse(status.enhancementConfigured)
        assertTrue(status.isComplete)

        val withText = preferencesOf(
            PreferenceKeys.ACTIVE_STT_PROVIDER_ID to "openai",
            PreferenceKeys.ACTIVE_STT_MODEL_ID to "whisper-1",
            PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID to "openai",
            PreferenceKeys.ACTIVE_TEXT_MODEL_ID to "gpt-4o-mini",
        )
        assertTrue(checker(system = system).compute(listOf(provider), withText).enhancementConfigured)
    }

    @Test
    fun compute_notificationsNeverBlockCompletion() {
        val system = FakeSystemChecks(imeEnabled = true, imeSelected = true, micGranted = true, notifications = false)

        assertTrue(checker(system = system).compute(listOf(provider), sttSelected).isComplete)
    }

    @Test
    fun compute_readsMicRequestedFlag() {
        val prefs = preferencesOf(PreferenceKeys.MIC_PERMISSION_REQUESTED to true)

        assertTrue(checker().compute(listOf(provider), prefs).micRequestedBefore)
        assertFalse(checker().compute(listOf(provider), preferencesOf()).micRequestedBefore)
    }

    @Test
    fun status_reEmitsOnRefreshAndSystemChanges() = runTest {
        val system = FakeSystemChecks()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val checker = SetupStatusChecker(
            systemChecks = system,
            providers = MutableStateFlow(listOf(provider)),
            preferences = MutableStateFlow(sttSelected),
            hasCredential = { true },
            dispatcher = dispatcher,
        )
        val emitted = mutableListOf<SetupStatus>()
        backgroundScope.launch(dispatcher) { checker.status.collect(emitted::add) }

        system.micGranted = true
        checker.refresh()
        system.imeSelected = true
        system.changeEvents.emit(Unit)

        assertEquals(listOf(false, true, true), emitted.map { it.micGranted })
        assertEquals(listOf(false, false, true), emitted.map { it.imeSelected })
    }

    @Test
    fun status_reEmitsOnSettingsChanges() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val prefs = MutableStateFlow<Preferences>(preferencesOf())
        val checker = SetupStatusChecker(
            systemChecks = FakeSystemChecks(),
            providers = MutableStateFlow(listOf(provider)),
            preferences = prefs,
            hasCredential = { true },
            dispatcher = dispatcher,
        )
        val emitted = mutableListOf<SetupStatus>()
        backgroundScope.launch(dispatcher) { checker.status.collect(emitted::add) }

        prefs.value = sttSelected

        assertEquals(listOf(false, true), emitted.map { it.sttProviderConfigured })
    }

    @Test
    fun status_skipsDuplicateStates() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val checker = SetupStatusChecker(
            systemChecks = FakeSystemChecks(),
            providers = MutableStateFlow(listOf(provider)),
            preferences = MutableStateFlow(sttSelected),
            hasCredential = { true },
            dispatcher = dispatcher,
        )
        val emitted = mutableListOf<SetupStatus>()
        backgroundScope.launch(dispatcher) { checker.status.collect(emitted::add) }

        checker.refresh()
        checker.refresh()

        assertEquals(1, emitted.size)
    }
}
