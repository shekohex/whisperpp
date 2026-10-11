package com.github.shekohex.whisperpp.command

enum class CommandDisclosureDecision {
    CONTINUE,
    CANCEL,
    OPEN_SETTINGS,
}

enum class CommandListeningStartResult {
    STARTED,
    CANCELLED,
    OPEN_SETTINGS,
    BLOCKED,
}

enum class CommandPostRecordingResult {
    TRANSFORMED,
    RETRY_LISTENING,
    BLOCKED,
}

class CommandDisclosureGateCoordinator(
    private val shouldBlockExternalSend: () -> Boolean,
) {
    suspend fun startListening(
        awaitDisclosure: suspend () -> CommandDisclosureDecision,
        startRecording: suspend () -> Unit,
    ): CommandListeningStartResult {
        return when (awaitDisclosure()) {
            CommandDisclosureDecision.CANCEL -> CommandListeningStartResult.CANCELLED
            CommandDisclosureDecision.OPEN_SETTINGS -> CommandListeningStartResult.OPEN_SETTINGS
            CommandDisclosureDecision.CONTINUE -> {
                if (shouldBlockExternalSend()) {
                    CommandListeningStartResult.BLOCKED
                } else {
                    startRecording()
                    CommandListeningStartResult.STARTED
                }
            }
        }
    }

    suspend fun finishListening(
        stopRecording: suspend () -> Unit,
        transcribeInstruction: suspend () -> String,
        restartListening: () -> Unit,
        transform: suspend (String) -> Unit,
    ): CommandPostRecordingResult {
        stopRecording()
        if (shouldBlockExternalSend()) {
            return CommandPostRecordingResult.BLOCKED
        }

        val instruction = transcribeInstruction().trim()
        if (instruction.isBlank()) {
            restartListening()
            return CommandPostRecordingResult.RETRY_LISTENING
        }

        if (shouldBlockExternalSend()) {
            return CommandPostRecordingResult.BLOCKED
        }

        transform(instruction)
        return CommandPostRecordingResult.TRANSFORMED
    }
}
