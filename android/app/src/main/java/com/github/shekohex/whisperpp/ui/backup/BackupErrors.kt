package com.github.shekohex.whisperpp.ui.backup

import androidx.annotation.StringRes
import com.github.shekohex.whisperpp.R
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

/** A failure whose user-facing text is already known (e.g. the picked file could not be opened). */
internal class BackupFileException(@StringRes val messageRes: Int) : IOException()

/** Maps a decrypt / parse failure to a user-facing message; technical details stay in the exception. */
@StringRes
internal fun importErrorMessage(error: Throwable): Int = when (error) {
    is BackupFileException -> error.messageRes
    is AEADBadTagException -> R.string.backup_error_wrong_password
    is GeneralSecurityException -> R.string.backup_error_unsupported_encryption
    is IOException -> R.string.backup_error_read_failed
    else -> R.string.backup_error_invalid_file
}

@StringRes
internal fun exportErrorMessage(error: Throwable): Int = when (error) {
    is BackupFileException -> error.messageRes
    is IOException -> R.string.backup_error_write_failed
    else -> R.string.backup_error_export_failed
}
