package com.github.shekohex.whisperpp.data

enum class AppendMode {
    APPEND,
    NO_APPEND,
}

data class PromptProfile(
    val id: String,
    val name: String,
    val promptAppend: String,
)

data class ProviderModelOverride(
    val providerId: String = "",
    val modelId: String = "",
) {
    fun isPresent(): Boolean = providerId.isNotBlank() || modelId.isNotBlank()
}

data class AppPromptMapping(
    val packageName: String,
    val profileId: String? = null,
    val appendMode: AppendMode = AppendMode.APPEND,
    val appPromptAppend: String? = null,
    val sttOverride: ProviderModelOverride? = null,
    val textOverride: ProviderModelOverride? = null,
)

enum class RuntimeWarningKind {
    MISSING_PROFILE,
    INVALID_PROVIDER,
    INVALID_MODEL,
    INCOMPATIBLE_MODEL_KIND,
    MISSING_SELECTION,
}

enum class RuntimeChannel {
    STT,
    TEXT,
}

data class RuntimeWarning(
    val kind: RuntimeWarningKind,
    val channel: RuntimeChannel? = null,
    val message: String,
)
