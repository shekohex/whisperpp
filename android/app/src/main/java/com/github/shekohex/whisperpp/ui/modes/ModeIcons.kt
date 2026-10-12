package com.github.shekohex.whisperpp.ui.modes

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.domain.modes.Mode

/** Material vector for a mode icon; shared by the Modes screens and the keyboard chip. */
val Mode.Icon.imageVector: ImageVector
    get() = when (this) {
        Mode.Icon.MIC -> Icons.Filled.Mic
        Mode.Icon.CHAT -> Icons.AutoMirrored.Filled.Chat
        Mode.Icon.MAIL -> Icons.Filled.Email
        Mode.Icon.EDIT -> Icons.Filled.Edit
        Mode.Icon.ASSISTANT -> Icons.Filled.SmartToy
        Mode.Icon.CODE -> Icons.Filled.Code
        Mode.Icon.NOTES -> Icons.AutoMirrored.Filled.Notes
        Mode.Icon.CUSTOM -> Icons.Filled.Tune
    }

@get:StringRes
val Mode.Icon.labelRes: Int
    get() = when (this) {
        Mode.Icon.MIC -> R.string.modes_icon_mic
        Mode.Icon.CHAT -> R.string.modes_icon_chat
        Mode.Icon.MAIL -> R.string.modes_icon_mail
        Mode.Icon.EDIT -> R.string.modes_icon_edit
        Mode.Icon.ASSISTANT -> R.string.modes_icon_assistant
        Mode.Icon.CODE -> R.string.modes_icon_code
        Mode.Icon.NOTES -> R.string.modes_icon_notes
        Mode.Icon.CUSTOM -> R.string.modes_icon_custom
    }
