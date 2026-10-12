package com.github.shekohex.whisperpp.updater

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy {
        UpdateRepository(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("updater.preferences_pb").also { it.delete() } })
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun emptyStateHasNoDownload() = runBlocking {
        val state = repository.updateState.first()
        assertEquals(PersistedUpdateState(-1L, null, null, null), state)
    }

    @Test
    fun downloadStateRoundTrips() = runBlocking {
        repository.saveDownloadState(42L, "sha256:abc", "1.2.0", "/tmp/whisperpp-1.2.0.apk")
        assertEquals(
            PersistedUpdateState(42L, "sha256:abc", "1.2.0", "/tmp/whisperpp-1.2.0.apk"),
            repository.updateState.first()
        )
        repository.clearDownloadState()
        assertEquals(PersistedUpdateState(-1L, null, null, null), repository.updateState.first())
    }

    @Test
    fun checkErrorPersistsAndClears() = runBlocking {
        assertNull(repository.lastCheckError.first())
        repository.saveCheckError("No APK available")
        assertEquals("No APK available", repository.lastCheckError.first())
        repository.clearDownloadState()
        assertEquals("No APK available", repository.lastCheckError.first())
        repository.saveCheckError(null)
        assertNull(repository.lastCheckError.first())
    }
}
