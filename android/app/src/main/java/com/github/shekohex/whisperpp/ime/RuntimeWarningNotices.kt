package com.github.shekohex.whisperpp.ime

import android.content.Context
import androidx.annotation.StringRes
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.RuntimeChannel
import com.github.shekohex.whisperpp.data.RuntimeWarning
import com.github.shekohex.whisperpp.data.RuntimeWarningKind

/**
 * Localized keyboard notice for the resolver warnings of [channel]. The resolver's own English
 * messages carry ids for logs; the user sees one translated sentence per warning kind.
 */
fun runtimeWarningNotice(context: Context, warnings: List<RuntimeWarning>, channel: RuntimeChannel): String =
    runtimeWarningMessages(warnings, channel).joinToString(separator = " ") { context.getString(it) }

/** Distinct message resources for [channel]'s warnings, in first-seen order. */
@StringRes
fun runtimeWarningMessages(warnings: List<RuntimeWarning>, channel: RuntimeChannel): List<Int> = warnings
    .filter { it.channel == channel }
    .map { it.kind.messageRes(channel) }
    .distinct()

@StringRes
private fun RuntimeWarningKind.messageRes(channel: RuntimeChannel): Int = when (this) {
    RuntimeWarningKind.MISSING_PROFILE -> R.string.keyboard_warning_missing_profile
    RuntimeWarningKind.INVALID_PROVIDER -> when (channel) {
        RuntimeChannel.STT -> R.string.keyboard_warning_stt_provider_missing
        RuntimeChannel.TEXT -> R.string.keyboard_warning_text_provider_missing
    }
    RuntimeWarningKind.INVALID_MODEL -> when (channel) {
        RuntimeChannel.STT -> R.string.keyboard_warning_stt_model_missing
        RuntimeChannel.TEXT -> R.string.keyboard_warning_text_model_missing
    }
    RuntimeWarningKind.INCOMPATIBLE_MODEL_KIND -> when (channel) {
        RuntimeChannel.STT -> R.string.keyboard_warning_stt_model_incompatible
        RuntimeChannel.TEXT -> R.string.keyboard_warning_text_model_incompatible
    }
    RuntimeWarningKind.MISSING_SELECTION -> when (channel) {
        RuntimeChannel.STT -> R.string.keyboard_warning_stt_selection_missing
        RuntimeChannel.TEXT -> R.string.keyboard_warning_text_selection_missing
    }
}
