package com.github.shekohex.whisperpp.ui.settings.common

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** A launchable app (or a manually added package) shown in per-app pickers. */
data class LauncherApp(
    val packageName: String,
    val label: String,
)

/** Loads launchable apps off the main thread, sorted by label. */
suspend fun loadLauncherApps(context: Context): List<LauncherApp> = withContext(Dispatchers.IO) {
    val packageManager = context.packageManager
    val queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    packageManager.queryIntentActivities(queryIntent, 0)
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName?.trim().orEmpty()
            if (packageName.isEmpty()) {
                null
            } else {
                val label = resolveInfo.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                LauncherApp(packageName = packageName, label = label.ifEmpty { packageName })
            }
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

/**
 * Installed apps plus [extraPackages] that are not installed (shown by package name), filtered by
 * [query] on label or package.
 */
fun mergeAndFilterApps(
    installed: List<LauncherApp>,
    extraPackages: Iterable<String>,
    query: String,
): List<LauncherApp> {
    val installedByPackage = installed.associateBy { it.packageName }
    val extras = extraPackages.asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !installedByPackage.containsKey(it) }
        .distinct()
        .map { LauncherApp(packageName = it, label = it) }
        .toList()
    val merged = (installed + extras)
        .distinctBy { it.packageName }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    val filter = query.trim().lowercase(Locale.US)
    if (filter.isBlank()) return merged
    return merged.filter {
        it.label.lowercase(Locale.US).contains(filter) ||
            it.packageName.lowercase(Locale.US).contains(filter)
    }
}
