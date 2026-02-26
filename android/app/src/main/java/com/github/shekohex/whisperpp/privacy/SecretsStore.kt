package com.github.shekohex.whisperpp.privacy

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretsStore(context: Context) {
    private val sharedPreferences = context.applicationContext.getSharedPreferences(SECRETS_PREFS_NAME, Context.MODE_PRIVATE)

    fun setProviderApiKey(providerId: String, apiKey: String) {
        if (providerId.isBlank()) {
            return
        }
        if (apiKey.isBlank()) {
            clearProviderApiKey(providerId)
            return
        }

        val encrypted = encrypt(apiKey) ?: return
        sharedPreferences.edit().putString(secretKey(providerId), encrypted).apply()
    }

    fun getProviderApiKey(providerId: String): String? {
        if (providerId.isBlank()) {
            return null
        }
        val encrypted = sharedPreferences.getString(secretKey(providerId), null) ?: return null
        return decrypt(encrypted)
    }

    fun clearProviderApiKey(providerId: String) {
        if (providerId.isBlank()) {
            return
        }
        sharedPreferences.edit().remove(secretKey(providerId)).apply()
    }

    fun getProviderApiKeyLast4(providerId: String): String? {
        val key = getProviderApiKey(providerId) ?: return null
        return key.takeLast(4)
    }

    private fun encrypt(value: String): String? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            val iv = cipher.iv
            val ivEncoded = Base64.encodeToString(iv, Base64.NO_WRAP)
            val ciphertextEncoded = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            "$ivEncoded:$ciphertextEncoded"
        } catch (_: Exception) {
            null
        }
    }

    private fun decrypt(payload: String): String? {
        return try {
            val parts = payload.split(DELIMITER, limit = 2)
            if (parts.size != 2) {
                return null
            }

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            val plaintext = cipher.doFinal(ciphertext)
            String(plaintext, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existingKey = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val keySpec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(256)
            .build()
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private fun secretKey(providerId: String): String {
        return "provider_api_key_$providerId"
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "whisperpp_provider_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val SECRETS_PREFS_NAME = "whisperpp_secrets"
        private const val DELIMITER = ":"
    }
}
