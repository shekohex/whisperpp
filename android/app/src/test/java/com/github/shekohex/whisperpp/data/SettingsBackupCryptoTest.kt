package com.github.shekohex.whisperpp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.Base64

class SettingsBackupCryptoTest {

    @Test
    fun encryptAndDecryptUtf8_roundTripsPayload() {
        val envelope = SettingsBackupCrypto.encryptUtf8(
            plaintext = "{\"hello\":\"world\"}",
            password = "correct horse battery staple",
            appVersionName = "0.1.3",
            exportedAtUtc = "2026-03-09T17:00:00Z",
        )

        val restored = SettingsBackupCrypto.decryptUtf8(
            envelope = envelope,
            password = "correct horse battery staple",
        )

        assertEquals("{\"hello\":\"world\"}", restored)
    }

    @Test
    fun decryptUtf8_wrongPasswordFails() {
        val envelope = SettingsBackupCrypto.encryptUtf8(
            plaintext = "secret",
            password = "correct horse battery staple",
            appVersionName = "0.1.3",
            exportedAtUtc = "2026-03-09T17:00:00Z",
        )

        assertThrows(GeneralSecurityException::class.java) {
            SettingsBackupCrypto.decryptUtf8(envelope, "wrong password")
        }
    }

    @Test
    fun decryptUtf8_tamperedCiphertextFails() {
        val envelope = SettingsBackupCrypto.encryptUtf8(
            plaintext = "secret",
            password = "correct horse battery staple",
            appVersionName = "0.1.3",
            exportedAtUtc = "2026-03-09T17:00:00Z",
        )

        val encodedPayload = Base64.getDecoder().decode(envelope.encryptedPayloadBase64)
        encodedPayload[0] = (encodedPayload[0].toInt() xor 0x01).toByte()

        val tamperedEnvelope = envelope.copy(
            encryptedPayloadBase64 = Base64.getEncoder().encodeToString(encodedPayload)
        )

        assertThrows(GeneralSecurityException::class.java) {
            SettingsBackupCrypto.decryptUtf8(tamperedEnvelope, "correct horse battery staple")
        }
    }

    @Test
    fun encryptUtf8_populatesStableMetadataAndHeaderValues() {
        val envelope = SettingsBackupCrypto.encryptUtf8(
            plaintext = "secret",
            password = "correct horse battery staple",
            appVersionName = "0.1.3",
            exportedAtUtc = "2026-03-09T17:00:00Z",
        )

        assertEquals(SETTINGS_BACKUP_SCHEMA_VERSION, envelope.schemaVersion)
        assertEquals("0.1.3", envelope.appVersionName)
        assertEquals("2026-03-09T17:00:00Z", envelope.exportedAtUtc)
        assertEquals(
            SETTINGS_BACKUP_CATEGORY_MANIFEST.map { it.id },
            envelope.categoryManifest.map { it.id },
        )
        assertEquals(SettingsBackupCrypto.CIPHER_ALGORITHM, envelope.crypto.algorithm)
        assertEquals(SettingsBackupCrypto.KDF_ALGORITHM, envelope.crypto.kdf.algorithm)
        assertEquals(SettingsBackupCrypto.PBKDF2_ITERATIONS, envelope.crypto.kdf.iterations)
        assertEquals(SettingsBackupCrypto.DERIVED_KEY_LENGTH_BITS, envelope.crypto.kdf.keyLengthBits)
        assertEquals(SettingsBackupCrypto.GCM_AUTH_TAG_BITS, envelope.crypto.authTagBits)
        assertTrue(envelope.crypto.kdf.saltBase64.isNotBlank())
        assertTrue(envelope.crypto.ivBase64.isNotBlank())
        assertTrue(envelope.encryptedPayloadBase64.isNotBlank())
        assertFalse(envelope.categoryManifest.first { it.id == SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS }.containsSensitiveContent)
        assertTrue(envelope.categoryManifest.first { it.id == SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS }.containsSensitiveContent)
    }

    @Test
    fun decryptUtf8_rejectsIterationsOutsideBounds() {
        val envelope = validEnvelope()

        listOf(99_999, 2_000_001, 1, 0, -1, Int.MAX_VALUE).forEach { iterations ->
            val tampered = envelope.withKdf { it.copy(iterations = iterations) }
            assertThrows(InvalidBackupCryptoParamsException::class.java) {
                SettingsBackupCrypto.decryptUtf8(tampered, PASSWORD)
            }
        }
    }

    @Test
    fun decryptUtf8_minimumIterationsPassValidationAndFailOnlyAuthentication() {
        val envelope = validEnvelope()
        // Avoid deriving with MAX_PBKDF2_ITERATIONS here to keep the test fast.
        val tampered = envelope.withKdf { it.copy(iterations = SettingsBackupCrypto.MIN_PBKDF2_ITERATIONS) }

        val error = assertThrows(GeneralSecurityException::class.java) {
            SettingsBackupCrypto.decryptUtf8(tampered, PASSWORD)
        }
        assertFalse(error is InvalidBackupCryptoParamsException)
    }

    @Test
    fun decryptUtf8_rejectsKeyLengthOtherThan256() {
        val envelope = validEnvelope()

        listOf(0, 64, 128, 192, 512).forEach { keyLengthBits ->
            val tampered = envelope.withKdf { it.copy(keyLengthBits = keyLengthBits) }
            assertThrows(InvalidBackupCryptoParamsException::class.java) {
                SettingsBackupCrypto.decryptUtf8(tampered, PASSWORD)
            }
        }
    }

    @Test
    fun decryptUtf8_rejectsShortSalt() {
        val envelope = validEnvelope()
        val tampered = envelope.withKdf { it.copy(saltBase64 = Base64.getEncoder().encodeToString(ByteArray(15))) }

        assertThrows(InvalidBackupCryptoParamsException::class.java) {
            SettingsBackupCrypto.decryptUtf8(tampered, PASSWORD)
        }
    }

    @Test
    fun decryptUtf8_rejectsIvOtherThan12Bytes() {
        val envelope = validEnvelope()

        listOf(0, 8, 11, 13, 16).forEach { ivLength ->
            val tampered = envelope.copy(
                crypto = envelope.crypto.copy(ivBase64 = Base64.getEncoder().encodeToString(ByteArray(ivLength)))
            )
            assertThrows(InvalidBackupCryptoParamsException::class.java) {
                SettingsBackupCrypto.decryptUtf8(tampered, PASSWORD)
            }
        }
    }

    @Test
    fun decryptUtf8_rejectsUnexpectedAuthTagLength() {
        val envelope = validEnvelope()
        val tampered = envelope.copy(crypto = envelope.crypto.copy(authTagBits = 96))

        assertThrows(InvalidBackupCryptoParamsException::class.java) {
            SettingsBackupCrypto.decryptUtf8(tampered, PASSWORD)
        }
    }

    private fun validEnvelope(): SettingsBackupEnvelope {
        return SettingsBackupCrypto.encryptUtf8(
            plaintext = "secret",
            password = PASSWORD,
            appVersionName = "0.1.3",
            exportedAtUtc = "2026-03-09T17:00:00Z",
        )
    }

    private fun SettingsBackupEnvelope.withKdf(
        transform: (SettingsBackupKdfHeader) -> SettingsBackupKdfHeader,
    ): SettingsBackupEnvelope {
        return copy(crypto = crypto.copy(kdf = transform(crypto.kdf)))
    }

    private companion object {
        const val PASSWORD = "correct horse battery staple"
    }
}
