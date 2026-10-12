package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.providers.withPathSuffix
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.TreeMap

/**
 * AssemblyAI streaming v3 (`/v3/ws`): audio must arrive in exact 50 ms frames, so PCM is re-chunked
 * (the tail is zero-padded at the commit); `Terminate` ends the session and `Termination` is the
 * done signal. Finished turns are kept by `turn_order`, so a re-sent (formatted) turn replaces the
 * earlier one.
 */
internal class AssemblyAiDialect(private val config: StreamingConfig) : StreamingDialect {
    private val turns = TreeMap<Int, String>()
    private val pending = Rechunker(FRAME_BYTES)
    private var done = false

    override val awaitsReady: Boolean = true

    override fun upgradeRequest(): Request {
        val url = config.baseUrl.withPathSuffix("v3", "ws").newBuilder().apply {
            addQueryParameter("sample_rate", SAMPLE_RATE.toString())
            addQueryParameter("encoding", "pcm_s16le")
            addQueryParameter("speech_model", config.modelId)
            addQueryParameter("mode", "balanced")
            config.language?.let { addQueryParameter("language_codes", JSONArray().put(it).toString()) }
            if (config.vocabulary.isNotEmpty()) {
                addQueryParameter("keyterms_prompt", JSONArray(config.vocabulary.take(MAX_KEYTERMS)).toString())
            }
        }.build()
        // AssemblyAI takes the bare key, without a scheme.
        return Request.Builder().url(url).header("Authorization", config.apiKey).build()
    }

    override fun audioFrames(pcm16: ByteArray): List<WsFrame> = pending.push(pcm16).map(WsFrame::Binary)

    override fun commitFrames(): List<WsFrame> {
        val tail = pending.flushPadded()
        val frames = ArrayList<WsFrame>()
        if (tail != null) frames += WsFrame.Binary(tail)
        frames += WsFrame.Text(JSONObject().put("type", "Terminate").toString())
        return frames
    }

    override fun onMessage(text: String): List<DialectEvent> {
        val json = RealtimeText.parse(text) ?: return emptyList()
        return when (json.optString("type")) {
            "Begin" -> listOf(DialectEvent.Ready)
            "Turn" -> onTurn(json)
            "Termination" -> if (done) emptyList() else {
                done = true
                listOf(DialectEvent.Done(RealtimeText.join(turns.values.toList())))
            }
            "Error" -> listOf(DialectEvent.Failed(RealtimeText.errorFor(config.baseUrl.host, json.optString("error_code"))))
            else -> emptyList()
        }
    }

    private fun onTurn(json: JSONObject): List<DialectEvent> {
        val order = json.optInt("turn_order")
        val transcript = json.optString("transcript").trim()
        if (json.optBoolean("end_of_turn")) {
            if (transcript.isNotEmpty()) turns[order] = transcript
            return listOf(DialectEvent.Partial(RealtimeText.join(turns.values.toList())))
        }
        val others = turns.filterKeys { it != order }.values.toList()
        return listOf(DialectEvent.Partial(RealtimeText.cumulative(others, transcript)))
    }

    companion object {
        private const val SAMPLE_RATE = 16_000

        /** 50 ms of 16 kHz PCM16, the frame size the API requires. */
        const val FRAME_BYTES = 1_600
        private const val MAX_KEYTERMS = 100
    }
}
