package com.github.shekohex.whisperpp.core.audio

import android.media.AudioManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFocusControllerTest {

    private class FakeGateway(var grant: Boolean = true) : AudioFocusGateway {
        val calls = mutableListOf<String>()
        var listener: ((Int) -> Unit)? = null

        override fun request(onFocusChange: (Int) -> Unit): Boolean {
            calls += "request"
            listener = onFocusChange
            return grant
        }

        override fun abandon() {
            calls += "abandon"
        }
    }

    @Test
    fun acquire_requestsFocusBeforeRecordingStarts() {
        val gateway = FakeGateway()
        val controller = AudioFocusController(gateway, MutableStateFlow(true))
        val events = mutableListOf<String>()

        if (controller.acquire()) events += "focus"
        events += "start"

        assertEquals(listOf("request"), gateway.calls)
        assertEquals(listOf("focus", "start"), events)
        assertTrue(controller.isHeld)
    }

    @Test
    fun release_abandonsOnEveryTerminalPath() {
        for (terminal in listOf("stop", "cancel", "error", "hidden", "destroy")) {
            val gateway = FakeGateway()
            val controller = AudioFocusController(gateway, MutableStateFlow(true))

            controller.acquire()
            controller.release()

            assertEquals("terminal=$terminal", listOf("request", "abandon"), gateway.calls)
            assertFalse(controller.isHeld)
        }
    }

    @Test
    fun release_isIdempotent() {
        val gateway = FakeGateway()
        val controller = AudioFocusController(gateway, MutableStateFlow(true))

        controller.acquire()
        controller.release()
        controller.release()

        assertEquals(listOf("request", "abandon"), gateway.calls)
    }

    @Test
    fun acquire_twiceRequestsOnce() {
        val gateway = FakeGateway()
        val controller = AudioFocusController(gateway, MutableStateFlow(true))

        controller.acquire()
        controller.acquire()

        assertEquals(listOf("request"), gateway.calls)
    }

    @Test
    fun deniedRequest_isNeverAbandoned() {
        val gateway = FakeGateway(grant = false)
        val controller = AudioFocusController(gateway, MutableStateFlow(true))

        assertFalse(controller.acquire())
        controller.release()

        assertEquals(listOf("request"), gateway.calls)
    }

    @Test
    fun disabledSetting_makesNoRequest() {
        val gateway = FakeGateway()
        val controller = AudioFocusController(gateway, MutableStateFlow(false))

        assertFalse(controller.acquire())
        controller.release()

        assertTrue(gateway.calls.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun focusLoss_isEmittedAndReacquireRequestsAgain() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeGateway()
        val controller = AudioFocusController(gateway, MutableStateFlow(true))
        controller.acquire()
        var losses = 0
        val job = launch { controller.focusLoss.first(); losses++ }

        gateway.listener?.invoke(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        controller.acquire()

        assertEquals(1, losses)
        assertEquals(listOf("request", "request"), gateway.calls)
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun focusChangeAfterRelease_isIgnored() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeGateway()
        val controller = AudioFocusController(gateway, MutableStateFlow(true))
        controller.acquire()
        controller.release()
        var losses = 0
        val job = launch { controller.focusLoss.collect { losses++ } }

        gateway.listener?.invoke(AudioManager.AUDIOFOCUS_LOSS)

        assertEquals(0, losses)
        job.cancel()
    }
}
