package com.github.shekohex.whisperpp.core.audio

/** Thin seam over [android.media.AudioManager] focus calls so focus policy can be unit-tested. */
interface AudioFocusGateway {
    /** Requests transient exclusive focus. Returns true when granted immediately. */
    fun request(onFocusChange: (Int) -> Unit): Boolean

    /** Abandons the focus previously obtained via [request]. */
    fun abandon()
}
