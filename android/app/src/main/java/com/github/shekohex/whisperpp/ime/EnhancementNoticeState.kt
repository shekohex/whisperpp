package com.github.shekohex.whisperpp.ime

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.shekohex.whisperpp.ui.keyboard.EnhancementNoticeStyle
import com.github.shekohex.whisperpp.ui.keyboard.EnhancementNoticeUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The transient enhancement notice strip: one message at a time, auto-dismissed after a short delay. */
class EnhancementNoticeState(private val scope: CoroutineScope) {
    private val _notice = mutableStateOf<EnhancementNoticeUiState?>(null)
    val notice: State<EnhancementNoticeUiState?> = _notice

    private var job: Job? = null

    fun dismiss() {
        job?.cancel()
        job = null
        _notice.value = null
    }

    fun show(message: String, style: EnhancementNoticeStyle) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return
        job?.cancel()
        _notice.value = EnhancementNoticeUiState(message = trimmed, style = style)
        job = scope.launch {
            delay(DISPLAY_MS)
            _notice.value = null
            job = null
        }
    }

    private companion object {
        const val DISPLAY_MS = 2600L
    }
}
