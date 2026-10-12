package com.github.shekohex.whisperpp.data

import com.github.shekohex.whisperpp.privacy.SecretsStore

interface ProviderCredentialSource {
    fun getProviderApiKey(providerId: String): String?
}

interface ProviderCredentialSink {
    fun setProviderApiKey(providerId: String, apiKey: String)

    fun clearProviderApiKey(providerId: String)
}

class SecretsStoreProviderCredentialSource(
    private val secretsStore: SecretsStore,
) : ProviderCredentialSource {
    override fun getProviderApiKey(providerId: String): String? {
        return secretsStore.getProviderApiKey(providerId)
    }
}

class SecretsStoreProviderCredentials(
    private val secretsStore: SecretsStore,
) : ProviderCredentialSource, ProviderCredentialSink {
    override fun getProviderApiKey(providerId: String): String? {
        return secretsStore.getProviderApiKey(providerId)
    }

    override fun setProviderApiKey(providerId: String, apiKey: String) {
        secretsStore.setProviderApiKey(providerId, apiKey)
    }

    override fun clearProviderApiKey(providerId: String) {
        secretsStore.clearProviderApiKey(providerId)
    }
}
