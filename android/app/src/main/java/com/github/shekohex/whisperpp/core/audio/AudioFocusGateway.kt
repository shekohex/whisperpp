package com.github.shekohex.whisperpp.core.audio

/** Thin seam over [android.media.AudioManager] focus calls so focus policy can be unit-tested. */
interface AudioFocusGateway {
    /**
     * Requests transient exclusive focus. Returns true when granted immediately. Callers pass the same
     * [onFocusChange] every time so one request/listener is reused instead of stacking new owners.
     */
    fun request(onFocusChange: (Int) -> Unit): Boolean

    /** Abandons the focus previously obtained via [request]; a no-op when nothing was requested. */
    fun abandon()
}
