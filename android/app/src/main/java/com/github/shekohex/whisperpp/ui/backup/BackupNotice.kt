package com.github.shekohex.whisperpp.ui.backup

import androidx.annotation.StringRes

/**
 * A status card shown in the export or import section. Text is kept as resource ids so the
 * ViewModel never resolves strings; [formatArgs] fill [message], [extraMessage] is appended.
 */
data class BackupNotice(
    @StringRes val title: Int,
    @StringRes val message: Int,
    val tone: Tone,
    val formatArgs: List<String> = emptyList(),
    @StringRes val extraMessage: Int? = null,
) {
    enum class Tone {
        Info,
        Success,
        Warning,
        Error,
    }
}
