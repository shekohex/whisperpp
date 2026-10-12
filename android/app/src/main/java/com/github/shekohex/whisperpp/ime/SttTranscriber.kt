package com.github.shekohex.whisperpp.ime

import android.content.Context
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.domain.providers.ProviderFactory
import com.github.shekohex.whisperpp.domain.providers.ScriptConversion
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.domain.providers.toProviderUserMessage
import java.io.File
import java.io.IOException

/** Batch transcription via [ProviderFactory], shared by dictation and command listening. */
class SttTranscriber(
    private val context: Context,
    private val providers: ProviderFactory,
) {
    /** Failures carry a user-facing message. */
    suspend fun transcribe(file: File, request: TranscriptionRequest): Result<String> {
        return providers.transcribe(file, request).fold(
            onSuccess = { Result.success(it.text) },
            onFailure = { Result.failure(IOException(it.toProviderUserMessage(context), it)) },
        )
    }

    /** The post-processing preference stores the localized option label. */
    fun scriptConversion(stored: String?): ScriptConversion = when (stored) {
        context.getString(R.string.settings_option_to_simplified) -> ScriptConversion.TO_SIMPLIFIED
        context.getString(R.string.settings_option_to_traditional) -> ScriptConversion.TO_TRADITIONAL
        else -> ScriptConversion.NONE
    }

    companion object {
        const val MEDIA_TYPE_WAV = "audio/wav"
    }
}
