package com.github.shekohex.whisperpp.command

import com.github.shekohex.whisperpp.CommandDisclosureDecision
import com.github.shekohex.whisperpp.CommandDisclosureGateCoordinator
import com.github.shekohex.whisperpp.CommandListeningStartResult
import com.github.shekohex.whisperpp.CommandPostRecordingResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandDisclosureFlowTest {

    @Test
    fun firstTimeCommandMode_requestsDisclosureBeforeRecordingStarts() = runBlocking {
        val events = mutableListOf<String>()
        val coordinator = CommandDisclosureGateCoordinator(
            shouldBlockExternalSend = {
                events += "gate_before_recording"
                false
            },
        )

        val outcome = coordinator.startListening(
            awaitDisclosure = {
                events += "disclosure_requested"
                CommandDisclosureDecision.CONTINUE
            },
            startRecording = {
                events += "recording_started"
            },
        )

        assertEquals(CommandListeningStartResult.STARTED, outcome)
        assertEquals(
            listOf("disclosure_requested", "gate_before_recording", "recording_started"),
            events,
        )
    }

    @Test
    fun cancelOrOpenPrivacySafety_exitsWithoutRecordingOrSending() = runBlocking {
        val cancelledEvents = mutableListOf<String>()
        val cancelledCoordinator = CommandDisclosureGateCoordinator(
            shouldBlockExternalSend = {
                cancelledEvents += "gate_before_recording"
                false
            },
        )

        val cancelled = cancelledCoordinator.startListening(
            awaitDisclosure = {
                cancelledEvents += "disclosure_requested"
                CommandDisclosureDecision.CANCEL
            },
            startRecording = {
                cancelledEvents += "recording_started"
            },
        )

        assertEquals(CommandListeningStartResult.CANCELLED, cancelled)
        assertFalse(cancelledEvents.contains("recording_started"))
        assertEquals(listOf("disclosure_requested"), cancelledEvents)

        val redirectedEvents = mutableListOf<String>()
        val redirectedCoordinator = CommandDisclosureGateCoordinator(
            shouldBlockExternalSend = {
                redirectedEvents += "gate_before_recording"
                false
            },
        )

        val redirected = redirectedCoordinator.startListening(
            awaitDisclosure = {
                redirectedEvents += "disclosure_requested"
                CommandDisclosureDecision.OPEN_SETTINGS
            },
            startRecording = {
                redirectedEvents += "recording_started"
            },
        )

        assertEquals(CommandListeningStartResult.OPEN_SETTINGS, redirected)
        assertFalse(redirectedEvents.contains("recording_started"))
        assertEquals(listOf("disclosure_requested"), redirectedEvents)
    }

    @Test
    fun afterConsent_sharedGateStillRunsBeforeRecordingTranscriptionAndTransform() = runBlocking {
        val events = mutableListOf<String>()
        val coordinator = CommandDisclosureGateCoordinator(
            shouldBlockExternalSend = {
                events += "gate_check"
                false
            },
        )

        val startOutcome = coordinator.startListening(
            awaitDisclosure = {
                events += "disclosure_requested"
                CommandDisclosureDecision.CONTINUE
            },
            startRecording = {
                events += "recording_started"
            },
        )

        val finishOutcome = coordinator.finishListening(
            stopRecording = {
                events += "recording_stopped"
            },
            transcribeInstruction = {
                events += "instruction_transcribed"
                "Rewrite politely"
            },
            restartListening = {
                events += "listening_restarted"
            },
            transform = { instruction ->
                events += "transform_called:$instruction"
            },
        )

        assertEquals(CommandListeningStartResult.STARTED, startOutcome)
        assertEquals(CommandPostRecordingResult.TRANSFORMED, finishOutcome)
        assertEquals(3, events.count { it == "gate_check" })
        assertTrue(events.indexOf("recording_started") < events.indexOf("instruction_transcribed"))
        assertTrue(events.indexOf("instruction_transcribed") < events.indexOf("transform_called:Rewrite politely"))
    }
}
