package com.github.shekohex.whisperpp.data

import androidx.annotation.StringRes
import com.github.shekohex.whisperpp.R

typealias TransformPresetId = String

data class TransformPreset(
    val id: TransformPresetId,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val promptInstruction: String,
)

const val TRANSFORM_PRESET_ID_CLEANUP: TransformPresetId = "cleanup"
const val TRANSFORM_PRESET_ID_SHORTEN: TransformPresetId = "shorten"
const val TRANSFORM_PRESET_ID_TONE_REWRITE: TransformPresetId = "tone_rewrite"

val TRANSFORM_PRESETS: List<TransformPreset> = listOf(
    TransformPreset(
        id = TRANSFORM_PRESET_ID_CLEANUP,
        titleRes = R.string.transform_preset_cleanup_title,
        descriptionRes = R.string.transform_preset_cleanup_description,
        promptInstruction = "Fix punctuation, capitalization, spelling, and spacing. Keep meaning and language. Do not add new information.",
    ),
    TransformPreset(
        id = TRANSFORM_PRESET_ID_SHORTEN,
        titleRes = R.string.transform_preset_shorten_title,
        descriptionRes = R.string.transform_preset_shorten_description,
        promptInstruction = "Shorten the text by removing filler and repetition. Keep key details and intent. Keep the same language.",
    ),
    TransformPreset(
        id = TRANSFORM_PRESET_ID_TONE_REWRITE,
        titleRes = R.string.transform_preset_tone_rewrite_title,
        descriptionRes = R.string.transform_preset_tone_rewrite_description,
        promptInstruction = "Rewrite with a clear, friendly, professional tone. Keep meaning and facts unchanged. Keep the same language.",
    ),
)

private val presetsById: Map<TransformPresetId, TransformPreset> = TRANSFORM_PRESETS.associateBy { it.id }

fun presetById(id: TransformPresetId?): TransformPreset? {
    val normalized = id?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return presetsById[normalized]
}
