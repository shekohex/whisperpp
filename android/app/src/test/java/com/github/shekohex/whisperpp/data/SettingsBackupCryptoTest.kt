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
}
