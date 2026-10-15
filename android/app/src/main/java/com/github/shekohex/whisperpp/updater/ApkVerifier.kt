package com.github.shekohex.whisperpp.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

object ApkVerifier {
    data class Identity(
        val packageName: String,
        val versionCode: Long,
        val versionName: String?,
        val signers: Set<String>,
        val signingHistory: Set<String> = signers,
    )

    fun validateIdentity(candidate: Identity, installed: Identity, release: LatestRelease): InstallFailure? = when {
        candidate.packageName != installed.packageName -> InstallFailure.WRONG_PACKAGE
        candidate.versionCode != release.versionCode.toLong() || candidate.versionName != release.version -> InstallFailure.WRONG_VERSION
        candidate.versionCode <= installed.versionCode -> InstallFailure.NOT_NEWER
        candidate.signers.isEmpty() || installed.signers.isEmpty() -> InstallFailure.INVALID_APK
        candidate.signers == installed.signers -> null
        candidate.signers.size == 1 && installed.signers.size == 1 &&
            candidate.signingHistory.containsAll(installed.signers) -> null
        else -> InstallFailure.SIGNATURE_MISMATCH
    }

    sealed class Result {
        data object Match : Result()
        data class Mismatch(val actual: String) : Result()
        data object InvalidExpected : Result()
        data class Unreadable(val cause: IOException) : Result()
    }

    private val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")

    suspend fun verify(file: File, expectedSignature: String): Result {
        val expected = expectedSignature.trim().removePrefix("sha256:")
        if (!SHA256_HEX.matches(expected)) return Result.InvalidExpected

        val actual = try {
            computeSha256(file)
        } catch (e: IOException) {
            return Result.Unreadable(e)
        }
        return if (actual.equals(expected, ignoreCase = true)) Result.Match else Result.Mismatch(actual)
    }

    suspend fun computeSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
