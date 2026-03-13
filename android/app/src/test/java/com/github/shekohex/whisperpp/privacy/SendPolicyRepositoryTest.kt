package com.github.shekohex.whisperpp.privacy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendPolicyRepositoryTest {

    @Test
    fun isBlockedFlow_defaultsToAllow_whenRuleDoesNotExist() = runBlocking {
        val repository = SendPolicyRepository(MutablePreferencesDataStore())

        assertFalse(repository.isBlockedFlow("com.example.notes").first())
        assertFalse(repository.isBlockedFlow(null).first())
        assertTrue(repository.getAllRulesFlow().first().isEmpty())
    }

    @Test
    fun setBlocked_normalizesTrimmedPackageName_andPersistsBlockedState() = runBlocking {
        val repository = SendPolicyRepository(MutablePreferencesDataStore())

        repository.setBlocked("  com.example.mail  ", true)

        assertTrue(repository.isBlockedFlow("com.example.mail").first())
        assertTrue(repository.isBlockedFlow("  com.example.mail ").first())
        assertEquals(mapOf("com.example.mail" to true), repository.getAllRulesFlow().first())
    }

    @Test
    fun setBlocked_false_removesExistingRule() = runBlocking {
        val repository = SendPolicyRepository(MutablePreferencesDataStore())

        repository.setBlocked("com.example.docs", true)
        repository.setBlocked("  com.example.docs  ", false)

        assertFalse(repository.isBlockedFlow("com.example.docs").first())
        assertTrue(repository.getAllRulesFlow().first().isEmpty())
    }

    private class MutablePreferencesDataStore(
        initialPreferences: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initialPreferences)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
