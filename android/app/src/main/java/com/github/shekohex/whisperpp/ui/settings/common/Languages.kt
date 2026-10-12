package com.github.shekohex.whisperpp.ui.settings.common

/** Language codes offered in settings pickers ("auto" lets the provider detect). */
val COMMON_LANGUAGES: List<Pair<String, String>> = listOf(
    "auto" to "Auto Detect",
    "en" to "English",
    "ar" to "Arabic",
    "zh" to "Chinese",
    "es" to "Spanish",
    "fr" to "French",
    "de" to "German",
    "ja" to "Japanese",
    "ko" to "Korean",
    "ru" to "Russian",
)

fun languageLabel(code: String): String = COMMON_LANGUAGES.firstOrNull { it.first == code }?.second ?: code
