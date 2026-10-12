package com.github.shekohex.whisperpp.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Status, `Retry-After` and fully-read body of a completed call. */
class RawResponse(
    val code: Int,
    val contentType: String?,
    val retryAfter: String?,
    val body: String,
)

/**
 * Enqueues the call and suspends until it completes. Cancelling the coroutine cancels the call.
 * The caller owns the returned [Response] and must close it.
 */
suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }
}

/**
 * Like [await], but the body is read on OkHttp's dispatcher thread before resuming, so cancelling
 * the coroutine also aborts a slow body download (G-01).
 */
suspend fun Call.awaitBody(): RawResponse {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val raw = try {
                    response.use {
                        RawResponse(
                            code = it.code,
                            contentType = it.header("Content-Type"),
                            retryAfter = it.header("Retry-After"),
                            body = it.body.string(),
                        )
                    }
                } catch (e: IOException) {
                    onFailure(call, e)
                    return
                }
                continuation.resume(raw)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }
}
