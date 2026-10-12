package com.github.shekohex.whisperpp.domain.providers

import com.github.shekohex.whisperpp.core.network.NetworkError

/** What a [StreamingTranscriptionSession] reports while it runs (§4.5.1). */
sealed interface StreamEvent {
    /** The stream is open and configured; audio flows from here on. */
    data object Connected : StreamEvent

    /** Best transcript so far for the current take; replaces any earlier partial. */
    data class Partial(val text: String) : StreamEvent

    /** Transcript of the committed audio; the same text [StreamingTranscriptionSession.commit] returns. */
    data class Final(val text: String) : StreamEvent

    /** The stream failed; [StreamingTranscriptionSession.commit] fails with the same error. */
    data class Error(val error: NetworkError) : StreamEvent

    /** The stream is closed; no further events follow. */
    data object Closed : StreamEvent
}
