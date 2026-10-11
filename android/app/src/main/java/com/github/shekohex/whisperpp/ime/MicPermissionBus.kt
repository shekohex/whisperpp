package com.github.shekohex.whisperpp.ime

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process channel from [PermissionRequestActivity] back to the IME (same process, so no
 * broadcast is needed).
 */
object MicPermissionBus {

    data class Result(val granted: Boolean, val retryAction: String?)

    private val mutableResults = MutableSharedFlow<Result>(extraBufferCapacity = 1)
    val results: SharedFlow<Result> = mutableResults.asSharedFlow()

    fun publish(result: Result) {
        mutableResults.tryEmit(result)
    }
}
