package com.github.shekohex.whisperpp.domain.modes

/** Where the dictated text goes. */
enum class OutputMode {
    /** At the cursor (replacing a selection the editor still has), as without modes. */
    INSERT,

    /** Replaces the text that was selected when recording started; plain insert when nothing was. */
    REPLACE_SELECTION,

    /** Copied to the clipboard; the field is left untouched. */
    CLIPBOARD,
}
