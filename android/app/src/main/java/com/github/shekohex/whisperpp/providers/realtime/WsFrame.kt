package com.github.shekohex.whisperpp.providers.realtime

/** One outgoing WebSocket frame produced by a [StreamingDialect]. */
internal sealed interface WsFrame {
    class Text(val payload: String) : WsFrame

    class Binary(val payload: ByteArray) : WsFrame
}
