package com.github.shekohex.whisperpp.ui.components

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.github.shekohex.whisperpp.core.permissions.MicPermission

/** [onClick] requests `RECORD_AUDIO`, or opens app settings once the user has denied it permanently. */
data class MicPermissionAction(
    val permanentlyDenied: Boolean,
    val onClick: () -> Unit,
)

/**
 * The only in-app mic requester (D-01): used by the onboarding Microphone page and the Home setup
 * checklist, always from an explicit button tap.
 */
@Composable
fun rememberMicPermissionAction(
    granted: Boolean,
    requestedBefore: Boolean,
    onRequested: () -> Unit,
    onResult: (granted: Boolean) -> Unit,
): MicPermissionAction {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        currentOnResult(it)
    }
    val status = MicPermission.resolveStatus(
        granted = granted,
        requestedBefore = requestedBefore,
        shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ?: false,
    )
    return MicPermissionAction(permanentlyDenied = status == MicPermission.Status.PERMANENTLY_DENIED) {
        when (status) {
            MicPermission.Status.REQUESTABLE -> {
                onRequested()
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
            MicPermission.Status.GRANTED,
            MicPermission.Status.PERMANENTLY_DENIED,
            -> context.startActivity(MicPermission.appSettingsIntent(context))
        }
    }
}
