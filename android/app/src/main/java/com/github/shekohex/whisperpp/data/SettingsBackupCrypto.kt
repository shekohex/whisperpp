package com.github.shekohex.whisperpp.data

import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SettingsBackupCrypto {
    const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    const val KDF_ALGORITHM = "PBKDF2WithHmacSHA1"
    const val PBKDF2_ITERATIONS = 120_000
    const val DERIVED_KEY_LENGTH_BITS = 256
    const val GCM_AUTH_TAG_BITS = 128
    private const val SALT_LENGTH_BYTES = 16
    private val secureRandom = SecureRandom()

    fun encryptPayload(
        plaintext: ByteArray,
        password: String,
        appVersionName: String,
        exportedAtUtc: String,
        categoryManifest: List<SettingsBackupCategoryManifestEntry> = SETTINGS_BACKUP_CATEGORY_MANIFEST,
    ): SettingsBackupEnvelope {
        require(password.isNotEmpty()) { "Password must not be empty" }

        val salt = randomBytes(SALT_LENGTH_BYTES)
        val key = deriveKey(password = password, salt = salt)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)

        return SettingsBackupEnvelope(
            schemaVersion = SETTINGS_BACKUP_SCHEMA_VERSION,
            appVersionName = appVersionName,
            exportedAtUtc = exportedAtUtc,
            categoryManifest = categoryManifest.toList(),
            crypto = SettingsBackupCryptoHeader(
                algorithm = CIPHER_ALGORITHM,
                ivBase64 = encodeBase64(cipher.iv),
                authTagBits = GCM_AUTH_TAG_BITS,
                kdf = SettingsBackupKdfHeader(
                    algorithm = KDF_ALGORITHM,
                    iterations = PBKDF2_ITERATIONS,
                    keyLengthBits = DERIVED_KEY_LENGTH_BITS,
                    saltBase64 = encodeBase64(salt),
                ),
            ),
            encryptedPayloadBase64 = encodeBase64(ciphertext),
        )
    }

    fun encryptUtf8(
        plaintext: String,
        password: String,
        appVersionName: String,
        exportedAtUtc: String,
        categoryManifest: List<SettingsBackupCategoryManifestEntry> = SETTINGS_BACKUP_CATEGORY_MANIFEST,
    ): SettingsBackupEnvelope {
        return encryptPayload(
            plaintext = plaintext.toByteArray(StandardCharsets.UTF_8),
            password = password,
            appVersionName = appVersionName,
            exportedAtUtc = exportedAtUtc,
            categoryManifest = categoryManifest,
        )
    }

    fun decryptPayload(
        envelope: SettingsBackupEnvelope,
        password: String,
    ): ByteArray {
        require(password.isNotEmpty()) { "Password must not be empty" }

        if (envelope.crypto.algorithm != CIPHER_ALGORITHM) {
            throw GeneralSecurityException("Unsupported cipher algorithm: ${envelope.crypto.algorithm}")
        }
        if (envelope.crypto.kdf.algorithm != KDF_ALGORITHM) {
            throw GeneralSecurityException("Unsupported KDF algorithm: ${envelope.crypto.kdf.algorithm}")
        }

        val salt = decodeBase64(envelope.crypto.kdf.saltBase64)
        val iv = decodeBase64(envelope.crypto.ivBase64)
        val ciphertext = decodeBase64(envelope.encryptedPayloadBase64)
        val key = deriveKey(
            password = password,
            salt = salt,
            iterations = envelope.crypto.kdf.iterations,
            keyLengthBits = envelope.crypto.kdf.keyLengthBits,
        )

        val cipher = Cipher.getInstance(envelope.crypto.algorithm)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(envelope.crypto.authTagBits, iv))
        return cipher.doFinal(ciphertext)
    }

    fun decryptUtf8(
        envelope: SettingsBackupEnvelope,
        password: String,
    ): String {
        return String(decryptPayload(envelope, password), StandardCharsets.UTF_8)
    }

    private fun deriveKey(
        password: String,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS,
        keyLengthBits: Int = DERIVED_KEY_LENGTH_BITS,
    ): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        return try {
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun randomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    private fun encodeBase64(value: ByteArray): String = Base64.getEncoder().encodeToString(value)

    private fun decodeBase64(value: String): ByteArray = Base64.getDecoder().decode(value)
}
