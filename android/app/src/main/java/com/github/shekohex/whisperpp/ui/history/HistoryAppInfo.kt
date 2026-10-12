package com.github.shekohex.whisperpp.ui.history

import androidx.compose.ui.graphics.ImageBitmap

/** Display data for the app a transcript was dictated into. */
data class HistoryAppInfo(
    val label: String,
    val icon: ImageBitmap?,
)

/** Looks an app up by package name; null when it is not installed or not visible to us. */
fun interface HistoryAppResolver {
    suspend fun resolve(packageName: String): HistoryAppInfo?
}
