package com.github.shekohex.whisperpp.dictation

import android.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class OpenAiRealtimeSttClient(
    private val okHttpClient: OkHttpClient,
    private val request: Request,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnected()
        fun onPartialTranscript(text: String)
        fun onFinalTranscript(text: String)
        fun onError(message: String)
        fun onClosed()
    }

    companion object {
        fun buildRequest(
            providerEndpoint: String,
            modelId: String,
            apiKey: String,
        ): Request? {
            val base = providerEndpoint.toHttpUrlOrNull() ?: return null

            val wsScheme = when (base.scheme) {
                "https" -> "wss"
                "http" -> "ws"
                "wss" -> "wss"
                "ws" -> "ws"
                else -> return null
            }

            val segments = base.pathSegments.filter { it.isNotBlank() }
            val derived = when {
                segments.size >= 2 && segments.takeLast(2) == listOf("v1", "realtime") -> base
                segments.isNotEmpty() && segments.last() == "v1" -> base.newBuilder().addPathSegment("realtime").build()
                else -> base.newBuilder().addPathSegments("v1/realtime").build()
            }

            val url = derived.newBuilder().scheme(wsScheme).apply {
                if (modelId.isNotBlank()) {
                    setQueryParameter("model", modelId)
                }
            }.build()

            return Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${apiKey}")
                .header("OpenAI-Beta", "realtime=v1")
                .build()
        }

        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .pingInterval(15, TimeUnit.SECONDS)
                .build()
        }
    }

    private val closed = AtomicBoolean(false)
    private var webSocket: WebSocket? = null
    private val partialBuffer = StringBuilder()

    fun connect() {
        if (closed.get()) return
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (closed.get()) return
                listener.onConnected()
                sendSessionUpdate(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (closed.get()) return
                handleEvent(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (closed.getAndSet(true)) return
                val message = t.message ?: "Realtime connection failed"
                listener.onError(message)
                listener.onClosed()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (closed.getAndSet(true)) return
                listener.onClosed()
            }
        })
    }

    fun sendPcm16le(pcm: ByteArray) {
        val socket = webSocket ?: return
        if (closed.get()) return
        val audio = Base64.encodeToString(pcm, Base64.NO_WRAP)
        val payload = JSONObject()
            .put("type", "input_audio_buffer.append")
            .put("audio", audio)
            .toString()
        socket.send(payload)
    }

    fun commitAudio() {
        val socket = webSocket ?: return
        if (closed.get()) return
        socket.send(JSONObject().put("type", "input_audio_buffer.commit").toString())
    }

    fun close() {
        if (closed.getAndSet(true)) return
        webSocket?.close(1000, "")
        webSocket = null
    }

    private fun sendSessionUpdate(webSocket: WebSocket) {
        val session = JSONObject()
            .put("input_audio_format", "pcm16")
            .put(
                "turn_detection",
                JSONObject().put("type", "server_vad")
            )
            .put(
                "input_audio_transcription",
                JSONObject().put("enabled", true)
            )

        val payload = JSONObject()
            .put("type", "session.update")
            .put("session", session)
            .toString()

        webSocket.send(payload)
    }

    private fun handleEvent(text: String) {
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return
        val type = obj.optString("type")

        when (type) {
            "conversation.item.input_audio_transcription.delta" -> {
                val delta = obj.optString("delta")
                if (delta.isNotBlank()) {
                    partialBuffer.append(delta)
                    val current = partialBuffer.toString()
                    if (current.isNotBlank()) {
                        listener.onPartialTranscript(current)
                    }
                } else {
                    val transcript = extractTranscript(obj)
                    if (!transcript.isNullOrBlank()) {
                        partialBuffer.clear()
                        partialBuffer.append(transcript)
                        listener.onPartialTranscript(transcript)
                    }
                }
            }

            "conversation.item.input_audio_transcription.completed" -> {
                val transcript = extractTranscript(obj)
                if (!transcript.isNullOrBlank()) {
                    partialBuffer.clear()
                    partialBuffer.append(transcript)
                    listener.onFinalTranscript(transcript)
                }
            }

            "error" -> {
                val message = obj.optJSONObject("error")?.optString("message")
                    ?: obj.optString("message")
                    ?: "Realtime error"
                listener.onError(message)
            }
        }
    }

    private fun extractTranscript(obj: JSONObject): String? {
        val direct = obj.optString("transcript")
        if (direct.isNotBlank()) return direct
        val text = obj.optString("text")
        if (text.isNotBlank()) return text
        val item = obj.optJSONObject("item")
        val content = item?.optJSONArray("content")
        if (content != null) {
            for (i in 0 until content.length()) {
                val entry = content.optJSONObject(i) ?: continue
                val maybe = entry.optString("transcript").ifBlank { entry.optString("text") }
                if (maybe.isNotBlank()) return maybe
            }
        }
        return null
    }
}
