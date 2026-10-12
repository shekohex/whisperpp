package com.github.shekohex.whisperpp.ui.backup

/** A status card shown in the export or import section. */
data class BackupNotice(
    val title: String,
    val message: String,
    val tone: Tone,
) {
    enum class Tone {
        Info,
        Success,
        Warning,
        Error,
    }
}
