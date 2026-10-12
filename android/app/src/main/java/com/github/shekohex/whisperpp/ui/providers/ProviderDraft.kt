package com.github.shekohex.whisperpp.ui.providers

import com.github.shekohex.whisperpp.core.network.isLocalNetworkHost
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.ThinkingType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The provider editor form. Persisted (as JSON) in the editor's `SavedStateHandle`, so it must not
 * hold secrets: the API key input lives only in memory in [ProviderEditViewModel].
 */
data class ProviderDraft(
    val name: String = "",
    val nameTouched: Boolean = false,
    val type: ProviderType = ProviderType.OPENAI,
    val baseUrl: String = "",
    val baseUrlTouched: Boolean = false,
    val authMode: ProviderAuthMode = ProviderAuthMode.API_KEY,
    val authModeTouched: Boolean = false,
    val models: List<ModelConfig> = emptyList(),
    val temperature: Float = 0.0f,
    val prompt: String = "",
    val languageCode: String = "auto",
    val timeout: Int = DEFAULT_TIMEOUT_MS,
    val thinkingEnabled: Boolean = false,
    val thinkingType: ThinkingType = ThinkingType.LEVEL,
    val thinkingBudget: Int = 4096,
    val thinkingLevel: String = "medium",
) {
    val parsedBaseUrl: HttpUrl? get() = baseUrl.trim().toHttpUrlOrNull()
    val isHttpsBaseUrl: Boolean get() = parsedBaseUrl?.scheme == "https"
    val isLocalHttpBaseUrl: Boolean
        get() = parsedBaseUrl?.let { it.scheme == "http" && isLocalNetworkHost(it.host) } == true
    val isRemoteHttpBaseUrl: Boolean get() = parsedBaseUrl?.scheme == "http" && !isLocalHttpBaseUrl
    val isBaseUrlValid: Boolean get() = parsedBaseUrl != null && (isHttpsBaseUrl || isLocalHttpBaseUrl)
    val isNameValid: Boolean get() = name.trim().isNotEmpty()

    fun toProvider(id: String, models: List<ModelConfig> = this.models): ServiceProvider = ServiceProvider(
        id = id,
        name = name.trim(),
        type = type,
        endpoint = baseUrl.trim(),
        authMode = authMode,
        apiKey = "",
        models = models,
        temperature = temperature,
        prompt = prompt,
        languageCode = languageCode,
        timeout = timeout,
        thinkingEnabled = thinkingEnabled,
        thinkingType = thinkingType,
        thinkingBudget = thinkingBudget,
        thinkingLevel = thinkingLevel,
    )

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000

        /** Draft for editing or duplicating [provider]; every field counts as touched. */
        fun from(provider: ServiceProvider) = ProviderDraft(
            name = provider.name,
            nameTouched = true,
            type = provider.type,
            baseUrl = provider.endpoint,
            baseUrlTouched = true,
            authMode = provider.authMode,
            authModeTouched = true,
            models = provider.models,
            temperature = provider.temperature,
            prompt = provider.prompt,
            languageCode = provider.languageCode,
            timeout = provider.timeout,
            thinkingEnabled = provider.thinkingEnabled,
            thinkingType = provider.thinkingType,
            thinkingBudget = provider.thinkingBudget,
            thinkingLevel = provider.thinkingLevel,
        )

        /** Draft for a brand-new provider of [type] with its default endpoint and auth. */
        fun newOf(type: ProviderType): ProviderDraft {
            val baseUrl = defaultBaseUrlFor(type)
            return ProviderDraft(
                name = defaultNameFor(type, baseUrl),
                type = type,
                baseUrl = baseUrl,
                authMode = defaultAuthModeFor(type),
            )
        }

        fun defaultBaseUrlFor(type: ProviderType): String = when (type) {
            ProviderType.OPENAI -> "https://api.openai.com/v1"
            ProviderType.GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
            ProviderType.WHISPER_ASR -> "https://YOUR_SERVER_IP:9000/asr"
            ProviderType.CUSTOM -> ""
        }

        fun defaultAuthModeFor(type: ProviderType): ProviderAuthMode = when (type) {
            ProviderType.WHISPER_ASR -> ProviderAuthMode.NO_AUTH
            else -> ProviderAuthMode.API_KEY
        }

        fun defaultNameFor(type: ProviderType, baseUrl: String): String {
            val host = baseUrl.trim().toHttpUrlOrNull()?.host?.takeIf { it.isNotBlank() }
            return if (host == null) type.toString() else "$type $host"
        }

        /** Endpoint without query/fragment/trailing slash, used to detect duplicate providers. */
        fun normalizedBaseUrl(value: String): String? {
            val parsed = value.trim().toHttpUrlOrNull() ?: return null
            return parsed.newBuilder().query(null).fragment(null).build().toString().trimEnd('/')
        }
    }
}
