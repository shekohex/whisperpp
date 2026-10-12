package com.github.shekohex.whisperpp.data

import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_BACKEND
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_MODEL
import com.github.shekohex.whisperpp.data.PreferenceKeys.MODEL
import com.github.shekohex.whisperpp.data.PreferenceKeys.SPEECH_TO_TEXT_BACKEND
import androidx.datastore.preferences.core.MutablePreferences
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun migrateLegacySelectionsToV2(prefs: MutablePreferences) {
    val hasAnyNewKey = prefs.contains(ACTIVE_STT_PROVIDER_ID) ||
        prefs.contains(ACTIVE_STT_MODEL_ID) ||
        prefs.contains(ACTIVE_TEXT_PROVIDER_ID) ||
        prefs.contains(ACTIVE_TEXT_MODEL_ID) ||
        prefs.contains(COMMAND_TEXT_PROVIDER_ID) ||
        prefs.contains(COMMAND_TEXT_MODEL_ID)
    if (hasAnyNewKey) {
        return
    }

    val legacySttProviderId = prefs[SPEECH_TO_TEXT_BACKEND]?.trim().orEmpty()
    val legacySttModelId = prefs[MODEL]?.trim().orEmpty()
    val legacyTextProviderId = prefs[ENHANCEMENT_BACKEND]?.trim().orEmpty()
    val legacyTextModelId = prefs[ENHANCEMENT_MODEL]?.trim().orEmpty()

    if (legacySttProviderId.isNotEmpty()) {
        prefs[ACTIVE_STT_PROVIDER_ID] = legacySttProviderId
    }
    if (legacySttModelId.isNotEmpty()) {
        prefs[ACTIVE_STT_MODEL_ID] = legacySttModelId
    }
    if (legacyTextProviderId.isNotEmpty()) {
        prefs[ACTIVE_TEXT_PROVIDER_ID] = legacyTextProviderId
    }
    if (legacyTextModelId.isNotEmpty()) {
        prefs[ACTIVE_TEXT_MODEL_ID] = legacyTextModelId
    }
}

internal fun extractProviderArray(rootElement: JsonElement): JsonArray? {
    if (rootElement.isJsonArray) {
        return rootElement.asJsonArray
    }
    if (!rootElement.isJsonObject) {
        return null
    }

    val rootObject = rootElement.asJsonObject
    rootObject.get("providers")?.let { providersElement ->
        if (providersElement.isJsonArray) {
            return providersElement.asJsonArray
        }
    }

    rootObject.entrySet().forEach { entry ->
        if (entry.value.isJsonArray) {
            return entry.value.asJsonArray
        }
    }
    return null
}

private fun rewriteEndpointToBaseUrlIfNeeded(endpoint: String): String {
    val trimmed = endpoint.trim()
    val parsed = trimmed.toHttpUrlOrNull() ?: return endpoint
    val segments = parsed.pathSegments.filter { it.isNotBlank() }

    val openAiBaseSegments = when {
        segments.size >= 2 && segments.takeLast(2) == listOf("audio", "transcriptions") -> segments.dropLast(2)
        segments.size >= 2 && segments.takeLast(2) == listOf("chat", "completions") -> segments.dropLast(2)
        else -> null
    }
    if (openAiBaseSegments != null && openAiBaseSegments.isNotEmpty()) {
        val basePath = "/" + openAiBaseSegments.joinToString("/")
        return parsed.newBuilder().encodedPath(basePath).query(null).fragment(null).build().toString()
    }

    if (trimmed.contains(":generateContent")) {
        val v1betaIndex = segments.indexOf("v1beta")
        if (v1betaIndex != -1) {
            val baseSegments = segments.take(v1betaIndex + 1)
            val basePath = "/" + baseSegments.joinToString("/")
            return parsed.newBuilder().encodedPath(basePath).query(null).fragment(null).build().toString()
        }
    }

    return endpoint
}

internal fun inferModelKindFromLegacyEndpoint(providerType: ProviderType, legacyEndpoint: String): ModelKind {
    if (providerType == ProviderType.WHISPER_ASR) {
        return ModelKind.STT
    }
    if (providerType == ProviderType.GEMINI) {
        return ModelKind.MULTIMODAL
    }

    val parsed = legacyEndpoint.trim().toHttpUrlOrNull()
    val segments = parsed?.pathSegments?.filter { it.isNotBlank() }.orEmpty()
    return if (segments.size >= 2 && segments.takeLast(2) == listOf("audio", "transcriptions")) {
        ModelKind.STT
    } else {
        ModelKind.TEXT
    }
}

internal fun migrateProvidersJsonToSchemaV2(rawProvidersJson: String): String? {
    val rootElement = JsonParser.parseString(rawProvidersJson)
    val providerArray = extractProviderArray(rootElement) ?: return null
    var changed = false

    providerArray.forEach providerLoop@ { providerElement ->
        if (!providerElement.isJsonObject) {
            return@providerLoop
        }

        val providerObject = providerElement.asJsonObject
        val providerType = providerObject.get("type")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            ?.let { raw -> runCatching { ProviderType.valueOf(raw) }.getOrNull() }
            ?: ProviderType.CUSTOM

        val legacyEndpoint = providerObject.get("endpoint")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            .orEmpty()

        if (legacyEndpoint.isNotBlank()) {
            val rewritten = rewriteEndpointToBaseUrlIfNeeded(legacyEndpoint)
            if (rewritten != legacyEndpoint) {
                providerObject.addProperty("endpoint", rewritten)
                changed = true
            }
        }

        val authModeElement = providerObject.get("authMode")
        val authModeRaw = authModeElement
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val hasValidAuthMode = authModeRaw != null && runCatching {
            ProviderAuthMode.valueOf(authModeRaw)
        }.isSuccess
        if (!hasValidAuthMode) {
            val defaultAuthMode = if (providerType == ProviderType.WHISPER_ASR) {
                ProviderAuthMode.NO_AUTH
            } else {
                ProviderAuthMode.API_KEY
            }
            providerObject.addProperty("authMode", defaultAuthMode.name)
            changed = true
        }

        providerObject.get("models")?.let { modelsElement ->
            if (modelsElement.isJsonArray) {
                val modelsArray = modelsElement.asJsonArray
                modelsArray.forEach modelLoop@ { modelElement ->
                    if (!modelElement.isJsonObject) {
                        return@modelLoop
                    }
                    val modelObject = modelElement.asJsonObject

                    val kindElement = modelObject.get("kind")
                    val kindRaw = kindElement
                        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                        ?.asString
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                    val hasValidKind = kindRaw != null && runCatching {
                        ModelKind.valueOf(kindRaw)
                    }.isSuccess
                    if (!hasValidKind) {
                        val inferredKind = inferModelKindFromLegacyEndpoint(providerType, legacyEndpoint)
                        modelObject.addProperty("kind", inferredKind.name)
                        changed = true
                    }

                    val streamingElement = modelObject.get("streamingPartialsSupported")
                    val hasValidStreamingFlag = streamingElement?.let {
                        it.isJsonPrimitive && it.asJsonPrimitive.isBoolean
                    } == true
                    if (!hasValidStreamingFlag) {
                        modelObject.addProperty("streamingPartialsSupported", false)
                        changed = true
                    }
                }
            }
        }
    }

    return if (changed) {
        Gson().toJson(rootElement)
    } else {
        null
    }
}

/**
 * Gson leaves fields that are missing from older JSON as null, so every value is re-checked here.
 * Models stored before schema v3 have no `capabilities`; derive them from the legacy fields.
 */
@Suppress("DEPRECATION", "USELESS_CAST", "SENSELESS_COMPARISON")
internal fun sanitizeCapabilities(model: ModelConfig, providerType: ProviderType, endpoint: String): ModelCapabilities {
    (model.capabilities as? ModelCapabilities)?.let { return it }
    return ModelCapabilities.fromLegacy(
        kind = (model.kind as? ModelKind) ?: inferModelKindFromLegacyEndpoint(providerType, endpoint),
        isThinking = (model.isThinking as? Boolean) ?: false,
        streamingPartialsSupported = (model.streamingPartialsSupported as? Boolean) ?: false,
        providerType = providerType,
        modelId = model.id,
    )
}

/**
 * Schema v3: adds `capabilities` to every model that lacks it (from `kind`, `isThinking`,
 * `streamingPartialsSupported`, and the Gemini model id) and rewrites those legacy fields as
 * mirrors of the result.
 * Returns null when nothing changed.
 */
internal fun migrateProvidersJsonToSchemaV3(rawProvidersJson: String): String? {
    val rootElement = JsonParser.parseString(rawProvidersJson)
    val providerArray = extractProviderArray(rootElement) ?: return null
    val gson = Gson()
    var changed = false

    providerArray.forEach providerLoop@ { providerElement ->
        if (!providerElement.isJsonObject) {
            return@providerLoop
        }
        val providerObject = providerElement.asJsonObject
        val providerType = providerObject.stringOrNull("type")
            ?.let { raw -> runCatching { ProviderType.valueOf(raw) }.getOrNull() }
            ?: ProviderType.CUSTOM
        val endpoint = providerObject.stringOrNull("endpoint").orEmpty()
        val modelsElement = providerObject.get("models")
        if (modelsElement == null || !modelsElement.isJsonArray) {
            return@providerLoop
        }
        modelsElement.asJsonArray.forEach modelLoop@ { modelElement ->
            if (!modelElement.isJsonObject) {
                return@modelLoop
            }
            val modelObject = modelElement.asJsonObject
            if (modelObject.get("capabilities")?.isJsonObject == true) {
                return@modelLoop
            }
            val kind = modelObject.stringOrNull("kind")
                ?.let { raw -> runCatching { ModelKind.valueOf(raw) }.getOrNull() }
                ?: inferModelKindFromLegacyEndpoint(providerType, endpoint)
            val capabilities = ModelCapabilities.fromLegacy(
                kind = kind,
                isThinking = modelObject.booleanOrNull("isThinking") ?: false,
                streamingPartialsSupported = modelObject.booleanOrNull("streamingPartialsSupported") ?: false,
                providerType = providerType,
                modelId = modelObject.stringOrNull("id").orEmpty(),
            )
            modelObject.add("capabilities", gson.toJsonTree(capabilities))
            modelObject.addProperty("kind", capabilities.legacyKind.name)
            modelObject.addProperty("isThinking", capabilities.thinking)
            modelObject.addProperty("streamingPartialsSupported", capabilities.realtime)
            changed = true
        }
    }

    return if (changed) gson.toJson(rootElement) else null
}

private fun JsonObject.stringOrNull(key: String): String? = get(key)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
    ?.asString
    ?.trim()
    ?.takeIf { it.isNotBlank() }

private fun JsonObject.booleanOrNull(key: String): Boolean? = get(key)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
    ?.asBoolean
