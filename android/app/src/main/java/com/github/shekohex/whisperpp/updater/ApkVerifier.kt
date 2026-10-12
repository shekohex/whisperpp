package com.github.shekohex.whisperpp.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

object ApkVerifier {
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
