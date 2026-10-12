package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
            listOf(PromptLibrary.EMAIL_ID, PromptLibrary.REWRITE_ID, PromptLibrary.ASSISTANT_ID),
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

    private class InMemoryDataStore(initial: Preferences = mutablePreferencesOf()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
