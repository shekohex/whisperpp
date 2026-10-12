package com.github.shekohex.whisperpp.ui.keyboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

data class FirstUseDisclosureUiState(
    val title: String,
    val dataSent: String,
    val endpointLines: List<String>,
    val contextLine: String,
)
