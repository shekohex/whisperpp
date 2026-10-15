package com.github.shekohex.whisperpp.providers

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

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

/** Keeps the body open so cancellation must interrupt a blocked socket read. */
internal class GatedSseServer(firstEvent: String) : AutoCloseable {
    private val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val url: String = "http://127.0.0.1:${serverSocket.localPort}/v1/responses"
    val clientDisconnected = CountDownLatch(1)

    init {
        thread(isDaemon = true, name = "gated-sse") {
            serverSocket.accept().use { socket ->
                socket.soTimeout = 15_000
                val input = socket.getInputStream()
                skipRequestIncludingBody(input)
                val output = socket.getOutputStream()
                output.write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n" +
                        "data: $firstEvent\n\n").toByteArray()
                )
                output.flush()
                try {
                    while (input.read() != -1) { /* drain until the client cancels */ }
                } catch (_: IOException) {
                    // A cancelled call usually tears the socket down with a reset, not a FIN.
                }
                clientDisconnected.countDown()
            }
        }
    }

    override fun close() {
        serverSocket.close()
    }

    private fun skipRequestIncludingBody(input: InputStream) {
        val head = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte == -1) return
            head.write(byte)
            val bytes = head.toByteArray()
            val n = bytes.size
            if (n >= 4 && bytes[n - 4] == '\r'.code.toByte() && bytes[n - 3] == '\n'.code.toByte() &&
                bytes[n - 2] == '\r'.code.toByte() && bytes[n - 1] == '\n'.code.toByte()
            ) {
                val length = Regex("content-length:\\s*(\\d+)", RegexOption.IGNORE_CASE)
                    .find(String(bytes, Charsets.ISO_8859_1))?.groupValues?.get(1)?.toIntOrNull() ?: 0
                var skipped = 0
                while (skipped < length && input.read() != -1) skipped++
                return
            }
        }
    }
}
