package com.github.shekohex.whisperpp.privacy

import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object PrivacyDisclosureFormatter {
    enum class Mode {
        DICTATION_AUDIO,
        ENHANCEMENT_TEXT,
        COMMAND_TEXT,
    }

    data class EndpointDisclosure(
        val baseUrl: String,
        val path: String,
    )

    data class ModeDisclosure(
        val mode: Mode,
        val title: String,
        val dataSent: String,
        val endpoints: List<EndpointDisclosure>,
        val contextLine: String,
    )

    fun disclosureForDictation(
        provider: ServiceProvider?,
        selectedModelId: String,
        useContext: Boolean,
    ): ModeDisclosure {
        val endpoint = resolveEndpoint(provider, selectedModelId, Mode.DICTATION_AUDIO)
        return ModeDisclosure(
            mode = Mode.DICTATION_AUDIO,
            title = "Dictation (audio)",
            dataSent = "Recorded audio is uploaded for transcription.",
            endpoints = listOf(endpoint),
            contextLine = if (useContext) {
                "Use Context is enabled. Recent text before cursor is sent with the request."
            } else {
                "Use Context is disabled. No before-cursor context text is sent."
            },
        )
    }

    fun disclosureForEnhancement(
        provider: ServiceProvider?,
        selectedModelId: String,
        useContext: Boolean,
    ): ModeDisclosure {
        val endpoint = resolveEndpoint(provider, selectedModelId, Mode.ENHANCEMENT_TEXT)
        return ModeDisclosure(
            mode = Mode.ENHANCEMENT_TEXT,
            title = "Enhancement / Smart Fix (text)",
            dataSent = "Transcript text is sent for refinement.",
            endpoints = listOf(endpoint),
            contextLine = if (useContext) {
                "Use Context is enabled. Context text may be sent with transcript text."
            } else {
                "Use Context is disabled. Only transcript text is sent."
            },
        )
    }

    fun disclosureForCommand(
        provider: ServiceProvider?,
        selectedModelId: String,
        useContext: Boolean,
    ): ModeDisclosure {
        val endpoint = resolveEndpoint(provider, selectedModelId, Mode.COMMAND_TEXT)
        return ModeDisclosure(
            mode = Mode.COMMAND_TEXT,
            title = "Command mode (text)",
            dataSent = "Command mode is planned. Selection text + spoken instruction will be sent when enabled.",
            endpoints = listOf(endpoint),
            contextLine = if (useContext) {
                "Use Context is enabled and may include nearby text when command mode is introduced."
            } else {
                "Use Context is disabled for command mode placeholders."
            },
        )
    }

    private fun resolveEndpoint(
        provider: ServiceProvider?,
        selectedModelId: String,
        mode: Mode,
    ): EndpointDisclosure {
        if (provider == null || provider.endpoint.isBlank()) {
            return EndpointDisclosure(baseUrl = "Not configured", path = "Not configured")
        }

        val parsed = provider.endpoint.toHttpUrlOrNull()
            ?: return EndpointDisclosure(baseUrl = "Invalid endpoint", path = "Invalid endpoint")

        val baseUrl = toBaseUrl(parsed)
        val path = when {
            mode != Mode.DICTATION_AUDIO && provider.type == ProviderType.GEMINI -> {
                geminiGenerateContentPath(parsed.encodedPath, selectedModelId)
            }
            else -> parsed.encodedPath.ifBlank { "/" }
        }

        return EndpointDisclosure(baseUrl = baseUrl, path = path)
    }

    private fun toBaseUrl(url: HttpUrl): String {
        val defaultPort = (url.scheme == "https" && url.port == 443) || (url.scheme == "http" && url.port == 80)
        return if (defaultPort) {
            "${url.scheme}://${url.host}"
        } else {
            "${url.scheme}://${url.host}:${url.port}"
        }
    }

    private fun geminiGenerateContentPath(basePath: String, selectedModelId: String): String {
        val normalizedBase = basePath.trim().ifBlank { "/" }.trimEnd('/')
        val model = selectedModelId.trim().ifEmpty { "{model}" }
        val actionSuffix = "/models/$model:generateContent"
        if (normalizedBase.endsWith(":generateContent") && normalizedBase.contains("/models/")) {
            return normalizedBase
        }
        return if (normalizedBase == "/") {
            actionSuffix
        } else {
            "$normalizedBase$actionSuffix"
        }
    }
}
