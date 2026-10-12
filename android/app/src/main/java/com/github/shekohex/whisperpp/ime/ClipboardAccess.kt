package com.github.shekohex.whisperpp.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.github.shekohex.whisperpp.R

/** System clipboard access for the keyboard: copying a transcript out and reading the primary clip in. */
class ClipboardAccess(private val context: Context) {
    private val clipboard: ClipboardManager
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun copy(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
    }

    /** The first clip item as trimmed text; null when the clipboard is empty or blank. */
    fun readTrimmedText(): String? {
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim().orEmpty()
        return text.takeIf { it.isNotBlank() }
    }
}
