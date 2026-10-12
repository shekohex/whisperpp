package com.github.shekohex.whisperpp.providers.realtime

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

/** In-memory [WebSocket] that records what the session sends and lets tests play the server. */
internal class FakeWsSocket(private val request: Request, val listener: WebSocketListener) : WebSocket {
    /** Every outgoing frame in order: [String] for text, [ByteString] for binary. */
    val frames = mutableListOf<Any>()
    var closeCode: Int? = null
    var cancelled = false
    var acceptSends = true

    override fun request(): Request = request

    override fun queueSize(): Long = 0

    override fun send(text: String): Boolean {
        if (!acceptSends) return false
        frames += text
        return true
    }

    override fun send(bytes: ByteString): Boolean {
        if (!acceptSends) return false
        frames += bytes
        return true
    }

    override fun close(code: Int, reason: String?): Boolean {
        closeCode = code
        return true
    }

    override fun cancel() {
        cancelled = true
    }

    val texts: List<String>
        get() = frames.filterIsInstance<String>()

    val json: List<JSONObject>
        get() = texts.map(::JSONObject)

    val binaries: List<ByteArray>
        get() = frames.filterIsInstance<ByteString>().map { it.toByteArray() }

    fun open() = listener.onOpen(this, response(101))

    fun receive(json: String) = listener.onMessage(this, json)

    fun remoteClose(code: Int = 1000) = listener.onClosing(this, code, "")

    fun fail(t: Throwable, code: Int? = null) = listener.onFailure(this, t, code?.let(::response))

    private fun response(code: Int) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .build()
}
