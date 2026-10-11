package com.github.shekohex.whisperpp.core.permissions

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MicPermissionTest {

    @Test
    fun requiredIsOnlyRecordAudio() {
        assertArrayEquals(arrayOf("android.permission.RECORD_AUDIO"), MicPermission.required())
    }

    @Test
    fun grantedWinsOverEverything() {
        assertEquals(
            MicPermission.Status.GRANTED,
            MicPermission.resolveStatus(granted = true, requestedBefore = true, shouldShowRationale = false),
        )
    }

    @Test
    fun neverRequestedIsRequestable() {
        assertEquals(
            MicPermission.Status.REQUESTABLE,
            MicPermission.resolveStatus(granted = false, requestedBefore = false, shouldShowRationale = false),
        )
    }

    @Test
    fun deniedOnceWithRationaleIsRequestable() {
        assertEquals(
            MicPermission.Status.REQUESTABLE,
            MicPermission.resolveStatus(granted = false, requestedBefore = true, shouldShowRationale = true),
        )
    }

    @Test
    fun requestedWithoutRationaleIsPermanentlyDenied() {
        assertEquals(
            MicPermission.Status.PERMANENTLY_DENIED,
            MicPermission.resolveStatus(granted = false, requestedBefore = true, shouldShowRationale = false),
        )
    }
}
