package com.github.shekohex.whisperpp.ui.settings.common

import android.content.Context
import android.content.pm.PackageManager

/** The installed `versionName`, or `"unknown"` when the package manager cannot tell. */
fun appVersionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: UNKNOWN_VERSION
} catch (_: PackageManager.NameNotFoundException) {
    UNKNOWN_VERSION
}

private const val UNKNOWN_VERSION = "unknown"
