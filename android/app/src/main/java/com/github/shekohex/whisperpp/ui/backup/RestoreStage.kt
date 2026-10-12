package com.github.shekohex.whisperpp.ui.backup

import android.net.Uri
import androidx.annotation.StringRes
import com.github.shekohex.whisperpp.data.RestoreMode

/** Where the import wizard currently is; the screen only renders the sheet/dialog this implies. */
sealed interface RestoreStage {
    data object Idle : RestoreStage

    /** File picked; waiting for Overwrite / Merge. */
    data class ChoosingMode(val uri: Uri) : RestoreStage

    /** Mode chosen; the password dialog is open. [error] is the last decrypt failure, if any. */
    data class EnteringPassword(
        val uri: Uri,
        val mode: RestoreMode,
        @StringRes val error: Int? = null,
    ) : RestoreStage

    data class Analyzing(val uri: Uri, val mode: RestoreMode) : RestoreStage
}

/** Where the export flow currently is. */
sealed interface ExportStage {
    data object Idle : ExportStage

    /** Password + confirmation dialog is open. */
    data object EnteringPassword : ExportStage

    /** Password accepted; the system "save as" picker is open. */
    data class ChoosingDestination(val password: String, val exportedAtUtc: String) : ExportStage
}
