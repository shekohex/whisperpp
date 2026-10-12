package com.github.shekohex.whisperpp.updater

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R

/** Localized message for a failed update check; [UpdateError.message] stays technical (logs). */
@Composable
fun updateErrorText(error: UpdateError): String = when (error) {
    is UpdateError.Network -> stringResource(R.string.update_error_network)
    is UpdateError.Server -> stringResource(R.string.update_error_server, error.httpStatus)
    is UpdateError.Client -> stringResource(R.string.update_error_client, error.httpStatus)
    is UpdateError.MalformedMetadata -> stringResource(R.string.update_error_metadata)
    is UpdateError.NoCompatibleApk -> stringResource(R.string.update_error_no_apk)
    is UpdateError.Unexpected -> stringResource(R.string.update_error_unexpected)
}

/** Localized message for a failed download or verification. */
@Composable
fun downloadFailureText(reason: DownloadFailure): String = stringResource(
    when (reason) {
        DownloadFailure.INSECURE_URL -> R.string.update_download_error_insecure
        DownloadFailure.STORAGE_UNAVAILABLE -> R.string.update_download_error_storage
        DownloadFailure.INSUFFICIENT_SPACE -> R.string.update_download_error_space
        DownloadFailure.START_FAILED -> R.string.update_download_error_start
        DownloadFailure.NOT_FOUND -> R.string.update_download_error_not_found
        DownloadFailure.NETWORK -> R.string.update_download_error_network
        DownloadFailure.FILE_ERROR -> R.string.update_download_error_file
        DownloadFailure.FILE_MISSING -> R.string.update_download_error_missing
        DownloadFailure.CHECKSUM_MISMATCH -> R.string.update_download_error_checksum
        DownloadFailure.INVALID_CHECKSUM -> R.string.update_download_error_invalid_checksum
        DownloadFailure.VERIFICATION_FAILED -> R.string.update_download_error_verification
        DownloadFailure.UNKNOWN -> R.string.update_download_error_unknown
    },
)
