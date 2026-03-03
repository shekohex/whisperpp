package com.github.shekohex.whisperpp.dictation

import android.os.Build
import android.view.inputmethod.EditorInfo

data class FocusKey(
    val packageName: String,
    val inputType: Int,
    val fieldId: Int?,
    val focusInstanceId: Long,
) {
    companion object {
        fun from(editorInfo: EditorInfo?, focusInstanceId: Long): FocusKey? {
            val pkg = editorInfo?.packageName?.trim().orEmpty()
            if (pkg.isBlank()) return null

            val fieldId = if (Build.VERSION.SDK_INT >= 28) {
                editorInfo?.fieldId
            } else {
                null
            }
            return FocusKey(
                packageName = pkg,
                inputType = editorInfo?.inputType ?: 0,
                fieldId = fieldId,
                focusInstanceId = focusInstanceId,
            )
        }
    }
}
