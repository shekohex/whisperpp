package com.github.shekohex.whisperpp.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MicPermissionGateTest {

    private var granted = false
    private val missingChanges = mutableListOf<Boolean>()
    private val gate = MicPermissionGate(
        isGranted = { granted },
        onMissingChanged = { missingChanges += it },
    )

    @Test
    fun checkPassesWhenGranted() {
        granted = true

        val allowed = gate.check(MicPermissionGate.ACTION_DICTATION) { error("must not run") }

        assertTrue(allowed)
        assertFalse(gate.isMissing)
        assertTrue(missingChanges.isEmpty())
    }

    @Test
    fun checkShowsBannerAndStoresPendingActionWhenMissing() {
        val allowed = gate.check(MicPermissionGate.ACTION_DICTATION) {}

        assertFalse(allowed)
        assertTrue(gate.isMissing)
        assertEquals(MicPermissionGate.ACTION_DICTATION, gate.pendingAction)
        assertEquals(listOf(true), missingChanges)
    }

    @Test
    fun deniedResultKeepsBannerAndPendingAction() {
        gate.check(MicPermissionGate.ACTION_DICTATION) {}

        gate.onResult(granted = false, retryAction = MicPermissionGate.ACTION_DICTATION)

        assertTrue(gate.isMissing)
        assertEquals(MicPermissionGate.ACTION_DICTATION, gate.pendingAction)
    }

    @Test
    fun grantedResultClearsBannerAndRetriesWhenInputViewStarts() {
        var retries = 0
        gate.check(MicPermissionGate.ACTION_DICTATION) { retries++ }
        granted = true

        gate.onResult(granted = true, retryAction = MicPermissionGate.ACTION_DICTATION)

        assertFalse(gate.isMissing)
        assertNull(gate.pendingAction)
        assertEquals(0, retries)

        gate.onInputViewStarted()
        gate.onInputViewStarted()

        assertEquals(1, retries)
        assertEquals(listOf(true, false), missingChanges)
    }

    @Test
    fun grantedResultForAnotherActionDoesNotRetry() {
        var retries = 0
        gate.check(MicPermissionGate.ACTION_DICTATION) { retries++ }
        granted = true

        gate.onResult(granted = true, retryAction = MicPermissionGate.ACTION_COMMAND)
        gate.onInputViewStarted()

        assertFalse(gate.isMissing)
        assertEquals(0, retries)
    }

    @Test
    fun inputViewStartClearsBannerWithoutRetryWhenGrantedInSettings() {
        var retries = 0
        gate.check(MicPermissionGate.ACTION_DICTATION) { retries++ }
        granted = true

        gate.onInputViewStarted()

        assertFalse(gate.isMissing)
        assertNull(gate.pendingAction)
        assertEquals(0, retries)
    }

    @Test
    fun inputViewStartKeepsBannerWhileStillDenied() {
        gate.check(MicPermissionGate.ACTION_DICTATION) {}

        gate.onInputViewStarted()

        assertTrue(gate.isMissing)
    }

    @Test
    fun denyThenRetapBannerThenGrant_retriesTheOriginalStart() {
        var retries = 0
        gate.check(MicPermissionGate.ACTION_DICTATION) { retries++ }

        gate.onResult(granted = false, retryAction = MicPermissionGate.ACTION_DICTATION)
        gate.onInputViewStarted()
        // Banner tapped again: the activity is launched for the still-pending action.
        val relaunchedFor = gate.pendingAction
        granted = true
        gate.onResult(granted = true, retryAction = relaunchedFor)
        gate.onInputViewStarted()

        assertEquals(MicPermissionGate.ACTION_DICTATION, relaunchedFor)
        assertEquals(1, retries)
        assertFalse(gate.isMissing)
    }

    @Test
    fun startingAnotherActionWithAccess_dropsTheOlderPendingRetry() {
        var retries = 0
        gate.check(MicPermissionGate.ACTION_DICTATION) { retries++ }
        granted = true

        assertTrue(gate.check(MicPermissionGate.ACTION_COMMAND, retry = null))
        gate.onResult(granted = true, retryAction = MicPermissionGate.ACTION_DICTATION)
        gate.onInputViewStarted()

        assertNull(gate.pendingAction)
        assertEquals(0, retries)
    }
}
