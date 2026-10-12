package com.github.shekohex.whisperpp.privacy

import android.content.res.Resources
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import androidx.annotation.StringRes
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Describes what each cloud mode sends and where. User-visible text is returned as string resource
 * ids (resolve with [title]/[endpointLine] helpers or `stringResource`) so the formatter stays
 * Android-free and testable; URLs and paths are plain data.
 */
object PrivacyDisclosureFormatter {
    enum class Mode {
        DICTATION_AUDIO,
        ENHANCEMENT_TEXT,
        COMMAND_TEXT,
    }

    enum class EndpointStatus {
        CONFIGURED,
        NOT_CONFIGURED,
        INVALID,
    }

    /** [baseUrl] and [path] are empty unless [status] is [EndpointStatus.CONFIGURED]. */
    data class EndpointDisclosure(
        @StringRes val label: Int? = null,
        val baseUrl: String,
        val path: String,
        val status: EndpointStatus = EndpointStatus.CONFIGURED,
    )

    data class ModeDisclosure(
        val mode: Mode,
        @StringRes val title: Int,
        @StringRes val dataSent: Int,
        val endpoints: List<EndpointDisclosure>,
        @StringRes val contextLine: Int,
    )

    fun disclosureForDictation(
        provider: ServiceProvider?,
        selectedModelId: String,
        useContext: Boolean,
    ): ModeDisclosure {
        val endpoint = resolveEndpoint(provider, selectedModelId, Mode.DICTATION_AUDIO)
        return ModeDisclosure(
            mode = Mode.DICTATION_AUDIO,
            title = R.string.privacy_disclosure_dictation_title,
            dataSent = R.string.privacy_disclosure_dictation_data,
            endpoints = listOf(endpoint),
            contextLine = if (useContext) {
                R.string.privacy_disclosure_dictation_context_on
            } else {
                R.string.privacy_disclosure_dictation_context_off
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
            title = R.string.privacy_disclosure_enhancement_title,
            dataSent = R.string.privacy_disclosure_enhancement_data,
            endpoints = listOf(endpoint),
            contextLine = if (useContext) {
                R.string.privacy_disclosure_enhancement_context_on
            } else {
                R.string.privacy_disclosure_enhancement_context_off
            },
        )
    }

    fun disclosureForCommand(
        sttProvider: ServiceProvider?,
        sttModelId: String,
        textProvider: ServiceProvider?,
        textModelId: String,
        useContext: Boolean,
    ): ModeDisclosure {
        val instructionAudioHop = resolveEndpoint(
            provider = sttProvider,
            selectedModelId = sttModelId,
            mode = Mode.DICTATION_AUDIO,
            label = R.string.privacy_endpoint_instruction_audio,
        )
        val textTransformHop = resolveEndpoint(
            provider = textProvider,
            selectedModelId = textModelId,
            mode = Mode.COMMAND_TEXT,
            label = R.string.privacy_endpoint_text_transform,
        )
        return ModeDisclosure(
            mode = Mode.COMMAND_TEXT,
            title = R.string.privacy_disclosure_command_title,
            dataSent = R.string.privacy_disclosure_command_data,
            endpoints = listOf(instructionAudioHop, textTransformHop),
            contextLine = if (useContext) {
                R.string.privacy_disclosure_command_context_on
            } else {
                R.string.privacy_disclosure_command_context_off
            },
        )
    }

    /**
     * One display line for [endpoint], e.g. "Endpoint: https://api.openai.com/v1/audio/transcriptions".
     * The URL is isolated as left-to-right text so it reads correctly inside RTL sentences.
     */
    fun endpointLine(resources: Resources, endpoint: EndpointDisclosure): String {
        val label = resources.getString(endpoint.label ?: R.string.privacy_endpoint_label)
        val target = when (endpoint.status) {
            EndpointStatus.CONFIGURED -> BidiFormatter.getInstance()
                .unicodeWrap("${endpoint.baseUrl}${endpoint.path}", TextDirectionHeuristics.LTR)
            EndpointStatus.NOT_CONFIGURED -> resources.getString(R.string.privacy_endpoint_not_configured)
            EndpointStatus.INVALID -> resources.getString(R.string.privacy_endpoint_invalid)
        }
        return resources.getString(R.string.privacy_endpoint_line, label, target)
    }

    private fun resolveEndpoint(
        provider: ServiceProvider?,
        selectedModelId: String,
        mode: Mode,
        @StringRes label: Int? = null,
    ): EndpointDisclosure {
        if (provider == null || provider.endpoint.isBlank()) {
            return EndpointDisclosure(label = label, baseUrl = "", path = "", status = EndpointStatus.NOT_CONFIGURED)
        }

        val parsed = provider.endpoint.toHttpUrlOrNull()
            ?: return EndpointDisclosure(label = label, baseUrl = "", path = "", status = EndpointStatus.INVALID)

        val baseUrl = toBaseUrl(parsed)
        val basePath = parsed.encodedPath.ifBlank { "/" }
        val path = when (mode) {
            Mode.DICTATION_AUDIO -> {
                if (provider.type == ProviderType.WHISPER_ASR) {
                    basePath
                } else {
                    appendPath(basePath, "/audio/transcriptions")
                }
            }

            Mode.ENHANCEMENT_TEXT,
            Mode.COMMAND_TEXT -> {
                if (provider.type == ProviderType.GEMINI) {
                    geminiGenerateContentPath(basePath, selectedModelId)
                } else {
                    appendPath(basePath, "/chat/completions")
                }
            }
        }

        return EndpointDisclosure(label = label, baseUrl = baseUrl, path = path)
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

    private fun appendPath(basePath: String, suffix: String): String {
        val normalizedBase = basePath.trim().ifBlank { "/" }.trimEnd('/')
        val normalizedSuffix = suffix.trim().trimStart('/')
        return if (normalizedBase == "/") {
            "/$normalizedSuffix"
        } else {
            "$normalizedBase/$normalizedSuffix"
        }
    }
}
