package com.github.shekohex.whisperpp.command

import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ModelKind
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandDisclosureFlowTest {

    @Test
    fun disclosureIsRequired_beforeRecordingStarts() {
        val disclosure = commandDisclosureFixture(useContext = false)
        val harness = CommandDisclosureFlowHarness(disclosureDecision = DisclosureDecision.CONTINUE)

        val outcome = harness.startListening(disclosure)

        assertEquals(CommandListeningOutcome.LISTENING, outcome)
        assertTrue(harness.recordingStarted)
        assertEquals(
            listOf("listening_requested", "disclosure_required", "recording_started"),
            harness.events,
        )
    }

    @Test
    fun cancelOrOpenSettings_preventsRecording() {
        val disclosure = commandDisclosureFixture(useContext = true)

        val cancelled = CommandDisclosureFlowHarness(disclosureDecision = DisclosureDecision.CANCEL)
        assertEquals(CommandListeningOutcome.CANCELLED, cancelled.startListening(disclosure))
        assertFalse(cancelled.recordingStarted)
        assertEquals(
            listOf("listening_requested", "disclosure_required", "cancelled"),
            cancelled.events,
        )

        val redirected = CommandDisclosureFlowHarness(disclosureDecision = DisclosureDecision.OPEN_SETTINGS)
        assertEquals(CommandListeningOutcome.OPEN_SETTINGS, redirected.startListening(disclosure))
        assertFalse(redirected.recordingStarted)
        assertEquals(
            listOf("listening_requested", "disclosure_required", "open_settings"),
            redirected.events,
        )
    }

    @Test
    fun commandDisclosure_includesInstructionAudioAndTextTransformHops() {
        val disclosure = commandDisclosureFixture(useContext = true)

        assertEquals(2, disclosure.hops.size)
        assertEquals("Instruction audio transcription", disclosure.hops[0].label)
        assertEquals("https://stt.example.com", disclosure.hops[0].endpoint.baseUrl)
        assertEquals("/v1/audio/transcriptions", disclosure.hops[0].endpoint.path)
        assertEquals("Text transform", disclosure.hops[1].label)
        assertEquals("https://generativelanguage.googleapis.com", disclosure.hops[1].endpoint.baseUrl)
        assertEquals("/v1beta/models/gemini-1.5-pro:generateContent", disclosure.hops[1].endpoint.path)
        assertTrue(disclosure.dataSent.contains("instruction audio", ignoreCase = true))
        assertTrue(disclosure.dataSent.contains("selected text", ignoreCase = true))
        assertTrue(disclosure.contextLine.contains("Context text may be sent"))
    }

    private fun commandDisclosureFixture(useContext: Boolean): CommandDisclosureSeam {
        val sttProvider = ServiceProvider(
            id = "stt",
            name = "STT",
            type = ProviderType.OPENAI,
            endpoint = "https://stt.example.com/v1",
            models = listOf(ModelConfig(id = "whisper-1", name = "Whisper 1", kind = ModelKind.STT)),
        )
        val textProvider = ServiceProvider(
            id = "text",
            name = "Gemini",
            type = ProviderType.GEMINI,
            endpoint = "https://generativelanguage.googleapis.com/v1beta",
            models = listOf(ModelConfig(id = "gemini-1.5-pro", name = "Gemini 1.5 Pro", kind = ModelKind.MULTIMODAL)),
        )
        val sttDisclosure = PrivacyDisclosureFormatter.disclosureForDictation(
            provider = sttProvider,
            selectedModelId = "whisper-1",
            useContext = false,
        )
        val textDisclosure = PrivacyDisclosureFormatter.disclosureForEnhancement(
            provider = textProvider,
            selectedModelId = "gemini-1.5-pro",
            useContext = useContext,
        )

        return CommandDisclosureSeam(
            hops = listOf(
                CommandDisclosureHop(
                    label = "Instruction audio transcription",
                    endpoint = sttDisclosure.endpoints.single(),
                ),
                CommandDisclosureHop(
                    label = "Text transform",
                    endpoint = textDisclosure.endpoints.single(),
                ),
            ),
            dataSent = "Command mode uploads instruction audio for transcription, then sends the selected text plus transcribed instruction for transformation.",
            contextLine = if (useContext) {
                "Context text may be sent with the selected text when Use Context is enabled."
            } else {
                "Only the selected text and transcribed instruction are sent when Use Context is disabled."
            },
        )
    }

    private enum class DisclosureDecision {
        CONTINUE,
        CANCEL,
        OPEN_SETTINGS,
    }

    private enum class CommandListeningOutcome {
        LISTENING,
        CANCELLED,
        OPEN_SETTINGS,
    }

    private data class CommandDisclosureHop(
        val label: String,
        val endpoint: PrivacyDisclosureFormatter.EndpointDisclosure,
    )

    private data class CommandDisclosureSeam(
        val hops: List<CommandDisclosureHop>,
        val dataSent: String,
        val contextLine: String,
    )

    private class CommandDisclosureFlowHarness(
        private val disclosureDecision: DisclosureDecision,
    ) {
        val events = mutableListOf<String>()
        var recordingStarted = false
            private set

        fun startListening(disclosure: CommandDisclosureSeam): CommandListeningOutcome {
            events += "listening_requested"
            if (disclosure.hops.isEmpty()) {
                throw IllegalStateException("command disclosure must exist before recording")
            }
            events += "disclosure_required"

            return when (disclosureDecision) {
                DisclosureDecision.CONTINUE -> {
                    recordingStarted = true
                    events += "recording_started"
                    CommandListeningOutcome.LISTENING
                }

                DisclosureDecision.CANCEL -> {
                    events += "cancelled"
                    CommandListeningOutcome.CANCELLED
                }

                DisclosureDecision.OPEN_SETTINGS -> {
                    events += "open_settings"
                    CommandListeningOutcome.OPEN_SETTINGS
                }
            }
        }
    }
}
