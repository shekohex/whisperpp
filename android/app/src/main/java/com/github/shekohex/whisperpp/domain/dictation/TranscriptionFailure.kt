package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.core.network.NetworkError
import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

private const val MAX_CAUSE_DEPTH = 8

/**
 * Whether a transcription failure is the network or the provider's service (offline, timeout, TLS,
 * rate limit, HTTP error) rather than a bad response or setup. Walks the cause chain: callers wrap
 * provider errors in a user-facing exception.
 */
fun Throwable.isNetworkFailure(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth++ < MAX_CAUSE_DEPTH) {
        when (current) {
            is NetworkError.InvalidResponse, is NetworkError.InsecureTransport -> return false
            is NetworkError -> return true
            is UnknownHostException, is SocketException, is SSLException -> return true
            is IOException -> if (current.javaClass.name.startsWith("java.net.")) return true
        }
        current = current.cause
    }
    return false
}

/** The history reason for a failed transcription call. */
fun Throwable.toTranscriptionFailureReason(): HistoryFailureReason =
    if (isNetworkFailure()) HistoryFailureReason.NETWORK_ERROR else HistoryFailureReason.TRANSCRIPTION_FAILED
