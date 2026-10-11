package com.github.shekohex.whisperpp.core.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Single source of truth for the permissions recording needs. Only `RECORD_AUDIO` gates the mic;
 * notifications are optional and requested elsewhere (D-03).
 */
object MicPermission {

    enum class Status {
        GRANTED,
        REQUESTABLE,
        PERMANENTLY_DENIED,
    }

    fun required(): Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    fun isGranted(context: Context): Boolean = required().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Android reports "don't show rationale" both before the first request and after a permanent
     * denial, so [requestedBefore] disambiguates the two.
     */
    fun resolveStatus(granted: Boolean, requestedBefore: Boolean, shouldShowRationale: Boolean): Status = when {
        granted -> Status.GRANTED
        requestedBefore && !shouldShowRationale -> Status.PERMANENTLY_DENIED
        else -> Status.REQUESTABLE
    }

    fun status(activity: Activity, requestedBefore: Boolean): Status = resolveStatus(
        granted = isGranted(activity),
        requestedBefore = requestedBefore,
        shouldShowRationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
    )

    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
}
