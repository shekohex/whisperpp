package com.github.shekohex.whisperpp.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal fun sanitizeBasePrompt(value: String?): String {
    val trimmed = value.orEmpty().trim()
    return if (trimmed.isBlank()) "" else trimmed
}

internal fun parsePromptProfilesJson(json: String?, gson: Gson): List<PromptProfile> {
    val raw = json?.trim().orEmpty()
    if (raw.isBlank()) {
        return emptyList()
    }

    val type = object : TypeToken<List<PromptProfile>>() {}.type
    return try {
        val decoded: List<PromptProfile>? = gson.fromJson(raw, type)
        sanitizePromptProfiles(decoded ?: emptyList())
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun sanitizePromptProfiles(profiles: List<PromptProfile>): List<PromptProfile> {
    return profiles.mapNotNull { profile ->
        val id = (profile.id as String?).orEmpty().trim()
        val name = (profile.name as String?).orEmpty().trim()
        val append = (profile.promptAppend as String?).orEmpty().trim()

        if (id.isBlank() || name.isBlank()) {
            return@mapNotNull null
        }

        PromptProfile(
            id = id,
            name = name,
            promptAppend = append,
        )
    }
}

internal fun parseAppPromptMappingsJson(json: String?, gson: Gson): List<AppPromptMapping> {
    val raw = json?.trim().orEmpty()
    if (raw.isBlank()) {
        return emptyList()
    }

    val type = object : TypeToken<List<AppPromptMapping>>() {}.type
    return try {
        val decoded: List<AppPromptMapping>? = gson.fromJson(raw, type)
        sanitizeAppPromptMappings(decoded ?: emptyList())
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun sanitizeAppPromptMappings(mappings: List<AppPromptMapping>): List<AppPromptMapping> {
    return mappings.mapNotNull { mapping ->
        val packageName = (mapping.packageName as String?).orEmpty().trim()
        if (packageName.isBlank()) {
            return@mapNotNull null
        }

        val profileId = mapping.profileId?.trim()?.takeIf { it.isNotBlank() }
        val appendMode = (mapping.appendMode as AppendMode?) ?: AppendMode.APPEND
        val appPromptAppend = mapping.appPromptAppend?.trim()?.takeIf { it.isNotBlank() }
        val sttOverride = mapping.sttOverride?.let {
            ProviderModelOverride(
                providerId = (it.providerId as String?).orEmpty().trim(),
                modelId = (it.modelId as String?).orEmpty().trim(),
            )
        }
        val textOverride = mapping.textOverride?.let {
            ProviderModelOverride(
                providerId = (it.providerId as String?).orEmpty().trim(),
                modelId = (it.modelId as String?).orEmpty().trim(),
            )
        }

        AppPromptMapping(
            packageName = packageName,
            profileId = profileId,
            appendMode = appendMode,
            appPromptAppend = appPromptAppend,
            sttOverride = sttOverride,
            textOverride = textOverride,
            autoSend = mapping.autoSend,
        )
    }
}
