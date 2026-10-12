package com.github.shekohex.whisperpp.ui.keyboard

data class FirstUseDisclosureUiState(
    val title: String,
    val dataSent: String,
    val endpointLines: List<String>,
    val contextLine: String,
)
