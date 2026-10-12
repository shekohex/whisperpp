package com.github.shekohex.whisperpp.updater

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UpdateCheckRunnerTest {
    private val release = LatestRelease(
        version = "2.0.0",
        versionCode = 20,
        pubDate = null,
        platforms = mapOf("universal" to PlatformInfo("sha256:x", "https://e.com/u.apk"))
    )
    private val persisted = mutableListOf<String?>()
    private val notified = mutableListOf<String>()

    private fun runner(source: UpdateCheckSource) =
        UpdateCheckRunner(source, { persisted += it }, { notified += it })

    private fun errorRunner(error: UpdateError) =
        runner { UpdateCheckResult.Error(error) }

    @Test
    fun availableNotifiesAndSucceeds() = runTest {
        val outcome = runner {
            UpdateCheckResult.Available(release, Abi.UNIVERSAL, release.platforms.getValue("universal"))
        }.run(UpdateChannel.STABLE)
        assertEquals(UpdateCheckRunner.Outcome.SUCCESS, outcome)
        assertEquals(listOf("2.0.0"), notified)
        assertEquals(listOf<String?>(null), persisted)
    }

    @Test
    fun upToDateSucceedsAndClearsStaleError() = runTest {
        val outcome = runner { UpdateCheckResult.UpToDate }.run(UpdateChannel.STABLE)
        assertEquals(UpdateCheckRunner.Outcome.SUCCESS, outcome)
        assertEquals(listOf<String?>(null), persisted)
        assertTrue(notified.isEmpty())
    }

    @Test
    fun transientErrorsRetryWithoutPersisting() = runTest {
        for (error in listOf(UpdateError.Network("down"), UpdateError.Server(503))) {
            assertEquals(UpdateCheckRunner.Outcome.RETRY, errorRunner(error).run(UpdateChannel.STABLE))
        }
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun permanentErrorsSucceedAndPersistMessage() = runTest {
        val errors = listOf(
            UpdateError.NoCompatibleApk(listOf("arm64-v8a")),
            UpdateError.MalformedMetadata("missing platforms"),
            UpdateError.Client(404),
            UpdateError.Unexpected(IllegalStateException("boom"))
        )
        for (error in errors) {
            assertEquals(UpdateCheckRunner.Outcome.SUCCESS, errorRunner(error).run(UpdateChannel.NIGHTLY))
        }
        assertEquals(errors.map { it.message }, persisted)
    }

    @Test
    fun thrownIoExceptionIsTreatedAsTransient() = runTest {
        val outcome = runner { throw IOException("reset") }.run(UpdateChannel.STABLE)
        assertEquals(UpdateCheckRunner.Outcome.RETRY, outcome)
    }

    @Test
    fun thrownUnexpectedExceptionIsPermanent() = runTest {
        val outcome = runner { throw IllegalStateException("bug") }.run(UpdateChannel.STABLE)
        assertEquals(UpdateCheckRunner.Outcome.SUCCESS, outcome)
        assertEquals(1, persisted.size)
    }

    @Test(expected = CancellationException::class)
    fun cancellationPropagates() = runTest {
        runner { throw CancellationException("cancelled") }.run(UpdateChannel.STABLE)
    }
}
