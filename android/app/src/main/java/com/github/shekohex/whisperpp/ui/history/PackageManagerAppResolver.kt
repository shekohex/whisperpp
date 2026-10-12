package com.github.shekohex.whisperpp.ui.history

import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resolves label + icon through the [PackageManager] on [dispatcher]. */
class PackageManagerAppResolver(
    private val packageManager: PackageManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HistoryAppResolver {
    override suspend fun resolve(packageName: String): HistoryAppInfo? = withContext(dispatcher) {
        try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            HistoryAppInfo(
                label = packageManager.getApplicationLabel(info).toString().ifBlank { packageName },
                icon = packageManager.getApplicationIcon(info).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX).asImageBitmap(),
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private companion object {
        const val ICON_SIZE_PX = 48
    }
}
