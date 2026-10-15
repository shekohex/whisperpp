package com.github.shekohex.whisperpp.updater

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class ApkVerifierTest {
    private val installed = ApkVerifier.Identity("com.example", 65, "nightly-65", setOf("release"))
    private val candidate = installed.copy(versionCode = 66, versionName = "nightly-66")
    private val release = LatestRelease("nightly-66", 66, null, platforms = emptyMap())

    @Test
    fun onlyExactNewerReleaseWithCompatibleSignatureIsInstallable() {
        assertEquals(null, ApkVerifier.validateIdentity(candidate, installed, release))
        assertEquals(InstallFailure.WRONG_VERSION, ApkVerifier.validateIdentity(installed, installed, release))
        assertEquals(InstallFailure.WRONG_VERSION, ApkVerifier.validateIdentity(candidate.copy(versionName = "old"), installed, release))
        assertEquals(InstallFailure.WRONG_PACKAGE, ApkVerifier.validateIdentity(candidate.copy(packageName = "other"), installed, release))
        assertEquals(InstallFailure.NOT_NEWER, ApkVerifier.validateIdentity(candidate, candidate, release))
        assertEquals(InstallFailure.SIGNATURE_MISMATCH, ApkVerifier.validateIdentity(candidate.copy(signers = setOf("debug"), signingHistory = setOf("debug")), installed, release))
        assertEquals(InstallFailure.INVALID_APK, ApkVerifier.validateIdentity(candidate.copy(signers = emptySet()), installed, release))
    }

    @Test
    fun signingRotationAcceptsInstalledSignerInVerifiedApkHistory_butNotAnUnrelatedSigner() {
        val rotated = candidate.copy(signers = setOf("new-release"), signingHistory = setOf("release", "new-release"))
        assertEquals(null, ApkVerifier.validateIdentity(rotated, installed, release))
        assertEquals(InstallFailure.SIGNATURE_MISMATCH, ApkVerifier.validateIdentity(rotated.copy(signingHistory = setOf("new-release")), installed, release))
    }

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
