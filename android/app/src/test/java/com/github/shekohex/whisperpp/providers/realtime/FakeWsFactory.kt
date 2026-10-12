package com.github.shekohex.whisperpp.providers.realtime

import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Hands out [FakeWsSocket]s and keeps them for inspection. */
internal class FakeWsFactory : WebSocket.Factory {
    val sockets = mutableListOf<FakeWsSocket>()

    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
        FakeWsSocket(request, listener).also { sockets += it }
}
