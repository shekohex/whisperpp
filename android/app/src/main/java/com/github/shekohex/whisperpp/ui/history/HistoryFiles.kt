package com.github.shekohex.whisperpp.ui.history

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

internal fun historyExportFileName(date: LocalDate): String = "whisperpp_history_$date.json"

/** Overwrites the SAF document at [uri] with [text]. */
internal suspend fun writeTextToUri(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the destination")
    output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}
