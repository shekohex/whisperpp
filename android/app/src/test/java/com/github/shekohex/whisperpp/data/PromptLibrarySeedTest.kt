package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.github.shekohex.whisperpp.data.modes.ModeSeeds
import com.github.shekohex.whisperpp.data.modes.ModesRepository
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptLibrarySeedTest {

    private val gson = Gson()

    @Test
    fun seed_addsBuiltInsOnce_andIsIdempotent() = runBlocking {
        val repository = SettingsRepository(InMemoryDataStore())

        repository.seedPromptLibraryIfNeeded()
        val first = repository.promptProfiles.first()
        repository.seedPromptLibraryIfNeeded()

        assertEquals(PromptLibrary.BUILT_IN.map { it.id }, first.map { it.id })
        assertEquals(first, repository.promptProfiles.first())
    }

    @Test
    fun seed_keepsUserProfiles_andNeverOverwritesAnEditedBuiltIn() = runBlocking {
        val user = PromptProfile(id = "mine", name = "Mine", promptAppend = "My prompt")
        val editedChat = PromptProfile(id = PromptLibrary.CHAT_ID, name = "My chat", promptAppend = "Edited")
        val repository = SettingsRepository(
            InMemoryDataStore(mutablePreferencesOf(PreferenceKeys.PROMPT_PROFILES_JSON to gson.toJson(listOf(user, editedChat))))
        )

        repository.seedPromptLibraryIfNeeded()

        val profiles = repository.promptProfiles.first()
        assertEquals(user, profiles[0])
        assertEquals(editedChat, profiles[1])
        assertEquals(
            listOf(PromptLibrary.EMAIL_ID, PromptLibrary.REWRITE_ID, PromptLibrary.ASSISTANT_ID, PromptLibrary.VIBE_CODING_ID),
            profiles.drop(2).map { it.id },
        )
    }

    @Test
    fun seed_doesNotRestoreBuiltInsTheUserDeleted() = runBlocking {
        val repository = SettingsRepository(InMemoryDataStore())
        repository.seedPromptLibraryIfNeeded()
        repository.deletePromptProfile(PromptLibrary.EMAIL_ID)

        repository.seedPromptLibraryIfNeeded()

        assertTrue(repository.promptProfiles.first().none { it.id == PromptLibrary.EMAIL_ID })
    }

    @Test
    fun chatTemplate_isVoiceInkVerbatim() {
        val chat = PromptLibrary.BUILT_IN.single { it.id == PromptLibrary.CHAT_ID }.promptAppend
        assertTrue(chat.startsWith("<TASK>\nRewrite <TRANSCRIPT> as an informal, concise, and conversational chat message.\n</TASK>"))
        assertTrue(chat.endsWith("- Do not add greetings or sign-offs.\n</RULES>"))
    }

    @Test
    fun vibeCodingTemplate_isVerbatim() {
        val body = PromptLibrary.VIBE_CODING_PROFILE.promptAppend
        assertTrue(body.startsWith("Clean up the <TRANSCRIPT> text from a programming session."))
        assertTrue(body.endsWith("The output should be ready for direct use without further editing."))
        assertTrue(body.contains("NEVER answer any questions you find in the <TRANSCRIPT>"))
        assertTrue(body.contains("6. DO NOT add em-dashes or hyphens"))
    }

    @Test
    fun seed_addsVibeCodingPresetAndMode_onceForExistingUsers() = runBlocking {
        val store = InMemoryDataStore(
            mutablePreferencesOf(
                PreferenceKeys.PROMPT_LIBRARY_SEEDED to true,
                PreferenceKeys.MODES_V1 to gson.toJson(listOf(ModeSeeds.DICTATION)),
            )
        )
        val repository = SettingsRepository(store)

        repository.seedPromptLibraryIfNeeded()

        val profiles = repository.promptProfiles.first()
        assertEquals(listOf(PromptLibrary.VIBE_CODING_ID), profiles.map { it.id })
        val modes = ModesRepository(store).modes.first()
        assertEquals(
            listOf(ModeSeeds.DICTATION_ID, ModeSeeds.VIBE_CODING_ID),
            modes.map { it.id },
        )
        assertEquals(PromptLibrary.VIBE_CODING_ID, modes.single { it.id == ModeSeeds.VIBE_CODING_ID }.promptId)
        assertEquals(Mode.Icon.CODE, modes.single { it.id == ModeSeeds.VIBE_CODING_ID }.icon)
        assertEquals(true, modes.single { it.id == ModeSeeds.VIBE_CODING_ID }.enhancementEnabled)

        repository.seedPromptLibraryIfNeeded()

        assertEquals(profiles, repository.promptProfiles.first())
        assertEquals(modes, ModesRepository(store).modes.first())
    }

    @Test
    fun seed_doesNotRestoreDeletedBuiltIns_whenSeedingVibeCoding() = runBlocking {
        val store = InMemoryDataStore(mutablePreferencesOf(PreferenceKeys.PROMPT_LIBRARY_SEEDED to true))
        val repository = SettingsRepository(store)
        repository.seedPromptLibraryIfNeeded()
        repository.deletePromptProfile(PromptLibrary.VIBE_CODING_ID)

        repository.seedPromptLibraryIfNeeded()

        assertTrue(repository.promptProfiles.first().none { it.id == PromptLibrary.VIBE_CODING_ID })
    }

    @Test
    fun seed_afterVibeCodingModeDeletion_staysDeleted() = runBlocking {
        val store = InMemoryDataStore(
            mutablePreferencesOf(
                PreferenceKeys.PROMPT_LIBRARY_SEEDED to true,
                PreferenceKeys.MODES_V1 to gson.toJson(listOf(ModeSeeds.DICTATION, ModeSeeds.VIBE_CODING)),
            )
        )
        val repository = SettingsRepository(store)
        val modesRepository = ModesRepository(store)
        repository.seedPromptLibraryIfNeeded()
        assertTrue(modesRepository.delete(ModeSeeds.VIBE_CODING_ID))

        repository.seedPromptLibraryIfNeeded()

        assertTrue(modesRepository.modes.first().none { it.id == ModeSeeds.VIBE_CODING_ID })
    }

    @Test
    fun seed_withoutStoredModes_leavesModesToTheSeedsOrLegacyMigration() = runBlocking {
        val store = InMemoryDataStore(mutablePreferencesOf(PreferenceKeys.PROMPT_LIBRARY_SEEDED to true))
        val repository = SettingsRepository(store)

        repository.seedPromptLibraryIfNeeded()

        assertTrue(repository.promptProfiles.first().any { it.id == PromptLibrary.VIBE_CODING_ID })
        assertNull(store.current[PreferenceKeys.MODES_V1])
        assertTrue(ModesRepository(store).modes.first().any { it.id == ModeSeeds.VIBE_CODING_ID })
    }

    private class InMemoryDataStore(initial: Preferences = mutablePreferencesOf()) : DataStore<Preferences> {
        val current: Preferences get() = state.value
        private val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
