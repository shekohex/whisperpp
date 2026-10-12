package com.github.shekohex.whisperpp.updater

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class UpdateCheckerTest {
    private lateinit var server: MockWebServer
    private lateinit var client: HttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = HttpClient(OkHttp) {
            engine {
                preconfigured = OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build()
            }
        }
    }

    @After
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    private fun checker(versionCode: Int = 1, abis: List<String> = listOf("arm64-v8a")) = UpdateChecker(
        httpClient = client,
        currentVersionCode = versionCode,
        supportedAbis = { abis },
        baseDownloadUrl = server.url("/download").toString().trimEnd('/'),
        apiBaseUrl = server.url("/api").toString().trimEnd('/')
    )

    private fun json(code: Int = 5, platforms: String = """"arm64-v8a":{"signature":"sha256:a","url":"https://e.com/a.apk"}""") =
        """{"version":"2.0","version_code":$code,"platforms":{$platforms},"changelog":"hi"}"""

    private fun error(result: UpdateCheckResult): UpdateError {
        assertTrue("expected Error but was $result", result is UpdateCheckResult.Error)
        return (result as UpdateCheckResult.Error).error
    }

    @Test
    fun newerReleaseIsAvailable() = runTest {
        server.enqueue(MockResponse().setBody(json()))
        val result = checker().checkForUpdate(UpdateChannel.STABLE)
        assertTrue(result is UpdateCheckResult.Available)
        assertEquals("/download/latest/latest.json", server.takeRequest().path)
    }

    @Test
    fun sameOrOlderVersionIsUpToDate() = runTest {
        server.enqueue(MockResponse().setBody(json(code = 5)))
        assertEquals(UpdateCheckResult.UpToDate, checker(versionCode = 5).checkForUpdate(UpdateChannel.STABLE))
    }

    @Test
    fun nightlyUsesNightlyTag() = runTest {
        server.enqueue(MockResponse().setBody(json()))
        checker().checkForUpdate(UpdateChannel.NIGHTLY)
        assertEquals("/download/nightly/latest.json", server.takeRequest().path)
    }

    @Test
    fun serverErrorsAreTransient() = runTest {
        for (code in listOf(500, 503, 408, 429)) {
            server.enqueue(MockResponse().setResponseCode(code))
            val err = error(checker().checkForUpdate(UpdateChannel.STABLE))
            assertTrue("HTTP $code", err.isTransient)
        }
    }

    @Test
    fun clientErrorsArePermanent() = runTest {
        for (code in listOf(400, 403, 404)) {
            server.enqueue(MockResponse().setResponseCode(code))
            val err = error(checker().checkForUpdate(UpdateChannel.STABLE))
            assertFalse("HTTP $code", err.isTransient)
        }
    }

    @Test
    fun timeoutIsTransient() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertTrue(error(checker().checkForUpdate(UpdateChannel.STABLE)).isTransient)
    }

    @Test
    fun connectionFailureIsTransient() = runTest {
        val c = checker()
        server.shutdown()
        assertTrue(error(c.checkForUpdate(UpdateChannel.STABLE)).isTransient)
    }

    @Test
    fun malformedMetadataIsPermanent() = runTest {
        server.enqueue(MockResponse().setBody("<html>oops</html>"))
        val err = error(checker().checkForUpdate(UpdateChannel.STABLE))
        assertTrue(err is UpdateError.MalformedMetadata)
        assertFalse(err.isTransient)
    }

    @Test
    fun missingPlatformsIsPermanent() = runTest {
        server.enqueue(MockResponse().setBody("""{"version":"2","version_code":9}"""))
        assertFalse(error(checker().checkForUpdate(UpdateChannel.STABLE)).isTransient)
    }

    @Test
    fun noApkForAbiIsPermanent() = runTest {
        server.enqueue(MockResponse().setBody(json(platforms = """"x86":{"signature":"s","url":"https://e.com/x"}""")))
        val err = error(checker().checkForUpdate(UpdateChannel.STABLE))
        assertTrue(err is UpdateError.NoCompatibleApk)
        assertFalse(err.isTransient)
    }

    @Test
    fun universalFallbackIsUsed() = runTest {
        server.enqueue(MockResponse().setBody(json(platforms = """"universal":{"signature":"s","url":"https://e.com/u"}""")))
        val result = checker().checkForUpdate(UpdateChannel.STABLE) as UpdateCheckResult.Available
        assertEquals(Abi.UNIVERSAL, result.deviceAbi)
    }

    @Test
    fun classifyHttpStatusBoundaries() {
        assertTrue(UpdateChecker.classifyHttpStatus(599).isTransient)
        assertFalse(UpdateChecker.classifyHttpStatus(499).isTransient)
    }
}
