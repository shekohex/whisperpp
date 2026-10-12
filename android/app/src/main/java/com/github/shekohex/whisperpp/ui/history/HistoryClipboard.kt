package com.github.shekohex.whisperpp.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle

private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

/** Copies [text] and flags the clip sensitive so keyboards and clipboard previews hide it. */
internal fun copyTranscript(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(null, text)
    clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    clipboard.setPrimaryClip(clip)
}

/** Opens the system share sheet with [text]. */
internal fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}
