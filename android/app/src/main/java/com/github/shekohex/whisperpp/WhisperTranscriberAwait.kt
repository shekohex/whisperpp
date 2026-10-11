package com.github.shekohex.whisperpp

import android.content.Context
import com.github.shekohex.whisperpp.domain.dictation.TranscriptionRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Suspend adapter over the callback API: failure stays a failure (F-07) and cancelling the caller
 * cancels the request. Replaced by a native suspend provider API in WP-2.1.
 */
suspend fun WhisperTranscriber.transcribe(
    context: Context,
    file: File,
    request: TranscriptionRequest,
): Result<String> = suspendCancellableCoroutine { continuation ->
    startAsync(
        context = context,
        filename = file.absolutePath,
        mediaType = request.mediaType,
        attachToEnd = "",
        contextPrompt = request.contextPrompt,
        languageCode = request.languageCode,
        provider = request.provider,
        modelId = request.modelId,
        postprocessing = request.postprocessing,
        addTrailingSpace = request.addTrailingSpace,
        timeout = request.timeoutMs,
        staticPrompt = request.staticPrompt,
        temperature = request.temperature,
        callback = { text ->
            if (continuation.isActive) continuation.resume(Result.success(text))
        },
        exceptionCallback = { message ->
            if (continuation.isActive) continuation.resume(Result.failure(IOException(message)))
        },
    )
    continuation.invokeOnCancellation { stop() }
}
