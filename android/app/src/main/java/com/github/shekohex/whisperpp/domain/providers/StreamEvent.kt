package com.github.shekohex.whisperpp.domain.providers

sealed interface StreamEvent {
    data object Connected : StreamEvent

    data class Partial(val text: String) : StreamEvent

    data class Final(val text: String) : StreamEvent

    data class Error(val message: String) : StreamEvent

    data object Closed : StreamEvent
}
