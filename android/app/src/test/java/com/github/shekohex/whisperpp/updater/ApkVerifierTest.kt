package com.github.shekohex.whisperpp.updater

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class ApkVerifierTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val content = "fake apk bytes".toByteArray()
    private val sha = MessageDigest.getInstance("SHA-256").digest(content)
        .joinToString("") { "%02x".format(it) }

    @Test
    fun matchingHashVerifiesWithOrWithoutPrefixAndAnyCase() = runTest {
        val file = tmp.newFile("a.apk").apply { writeBytes(content) }
        assertEquals(ApkVerifier.Result.Match, ApkVerifier.verify(file, "sha256:$sha"))
        assertEquals(ApkVerifier.Result.Match, ApkVerifier.verify(file, sha.uppercase()))
    }

    @Test
    fun mismatchIsReportedWithActualHash() = runTest {
        val file = tmp.newFile("b.apk").apply { writeBytes(content) }
        val result = ApkVerifier.verify(file, "sha256:" + "0".repeat(64))
        assertEquals(ApkVerifier.Result.Mismatch(sha), result)
    }

    @Test
    fun malformedExpectedHashIsRejected() = runTest {
        val file = tmp.newFile("c.apk").apply { writeBytes(content) }
        assertEquals(ApkVerifier.Result.InvalidExpected, ApkVerifier.verify(file, "sha256:xyz"))
        assertEquals(ApkVerifier.Result.InvalidExpected, ApkVerifier.verify(file, ""))
    }

    @Test
    fun missingFileIsUnreadable() = runTest {
        val result = ApkVerifier.verify(tmp.root.resolve("nope.apk"), "sha256:$sha")
        assertTrue(result is ApkVerifier.Result.Unreadable)
    }
}
