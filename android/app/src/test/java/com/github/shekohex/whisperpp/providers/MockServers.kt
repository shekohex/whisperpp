package com.github.shekohex.whisperpp.providers

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

/** MockWebServer on loopback so the cleartext transport guard (J-01) allows it. */
internal fun startLoopbackServer(): MockWebServer = MockWebServer().apply {
    start(InetAddress.getByName("127.0.0.1"), 0)
}

internal fun MockWebServer.baseUrl(path: String = "/v1"): String =
    url(path).newBuilder().host("127.0.0.1").build().toString().trimEnd('/')

/** Client that records every [Call] it runs, to assert cancellation. */
internal class RecordingClient {
    val calls: MutableList<Call> = CopyOnWriteArrayList()
    val client: OkHttpClient = OkHttpClient().newBuilder()
        .addInterceptor { chain ->
            calls += chain.call()
            chain.proceed(chain.request())
        }
        .build()
}

internal fun tempAudioFile(): File = File.createTempFile("audio", ".wav").apply {
    writeBytes(byteArrayOf(1, 2, 3, 4))
    deleteOnExit()
}
