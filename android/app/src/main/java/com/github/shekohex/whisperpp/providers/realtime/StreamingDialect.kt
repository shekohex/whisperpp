package com.github.shekohex.whisperpp.providers.realtime

import okhttp3.Request

/**
 * Wire protocol of one cloud realtime STT provider. [WebSocketStreamingSession] owns the socket
 * lifecycle and calls the dialect under its lock, so implementations keep plain mutable state.
 * All audio is 16 kHz mono PCM16 little-endian.
 */
internal interface StreamingDialect {
    /** Upgrade request with URL (http(s); OkHttp upgrades it) and auth headers. Throws when misconfigured. */
    fun upgradeRequest(): Request

    /** Frames sent as soon as the socket is open (session config). */
    fun openFrames(): List<WsFrame> = emptyList()

    /** True when audio must wait for [DialectEvent.Ready] (the server confirms the session first). */
    val awaitsReady: Boolean
        get() = false

    /** Frames sent once ready, before any buffered audio. */
    fun readyFrames(): List<WsFrame> = emptyList()

    /** Frames carrying [pcm16]; may be empty while re-chunking. */
    fun audioFrames(pcm16: ByteArray): List<WsFrame>

    /** Flushes buffered audio and asks the server to finalize. Called once. */
    fun commitFrames(): List<WsFrame>

    fun onMessage(text: String): List<DialectEvent>

    /** The server closed the socket after the commit without an explicit done message. */
    fun onRemoteClose(): DialectEvent.Done? = null

    /** Frame the session sends periodically while recording, or null. */
    val keepAlive: KeepAlive?
        get() = null

    class KeepAlive(val frame: WsFrame, val intervalMs: Long)
}
