package com.github.shekohex.whisperpp.ui.providers

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ModelKind
import com.github.shekohex.whisperpp.data.ProviderType

/** Localized label for a provider type (brand names stay untranslated). */
@StringRes
internal fun ProviderType.labelRes(): Int = when (this) {
    ProviderType.OPENAI -> R.string.provider_type_openai
    ProviderType.WHISPER_ASR -> R.string.provider_type_whisper_asr
    ProviderType.GEMINI -> R.string.provider_type_gemini
    ProviderType.CUSTOM -> R.string.provider_type_custom
    ProviderType.DEEPGRAM -> R.string.provider_type_deepgram
    ProviderType.ELEVENLABS -> R.string.provider_type_elevenlabs
    ProviderType.MISTRAL -> R.string.provider_type_mistral
    ProviderType.SONIOX -> R.string.provider_type_soniox
    ProviderType.SPEECHMATICS -> R.string.provider_type_speechmatics
    ProviderType.ASSEMBLYAI -> R.string.provider_type_assemblyai
    ProviderType.XAI -> R.string.provider_type_xai
    ProviderType.CARTESIA -> R.string.provider_type_cartesia
}

@StringRes
internal fun ModelKind.labelRes(): Int = when (this) {
    ModelKind.STT -> R.string.provider_model_kind_stt
    ModelKind.TEXT -> R.string.provider_model_kind_text
    ModelKind.MULTIMODAL -> R.string.provider_model_kind_multimodal
}

/** Localized label for an API thinking level id; unknown ids are shown as-is. */
@Composable
internal fun thinkingLevelLabel(level: String): String = when (level) {
    "minimal" -> stringResource(R.string.provider_thinking_level_minimal)
    "low" -> stringResource(R.string.provider_thinking_level_low)
    "medium" -> stringResource(R.string.provider_thinking_level_medium)
    "high" -> stringResource(R.string.provider_thinking_level_high)
    "xhigh" -> stringResource(R.string.provider_thinking_level_xhigh)
    else -> level
}

/** Joins selection names (from [selectionReferencesProvider]/[selectionReferencesModel]) for display. */
@Composable
internal fun selectionNames(refs: List<Int>): String {
    val separator = stringResource(R.string.provider_list_separator)
    return refs.map { stringResource(it) }.joinToString(separator)
}
