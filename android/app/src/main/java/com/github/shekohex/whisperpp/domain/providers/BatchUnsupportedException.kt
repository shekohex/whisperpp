package com.github.shekohex.whisperpp.domain.providers

import android.content.Context
import com.github.shekohex.whisperpp.R

/**
 * The provider only transcribes live over a socket, so the recorded file cannot be sent as a batch
 * (the fallback after a failed stream). [providerLabel] is the brand name shown to the user.
 */
class BatchUnsupportedException(val providerLabel: String) :
    Exception("Batch transcription not supported for $providerLabel") {
    fun toUserMessage(context: Context): String =
        context.getString(R.string.provider_error_batch_unsupported, providerLabel)
}
