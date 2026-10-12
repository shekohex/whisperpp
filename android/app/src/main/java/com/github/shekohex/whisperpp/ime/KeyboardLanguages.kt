package com.github.shekohex.whisperpp.ime

import java.util.Locale

/** Stored value for "let the provider detect the language". */
const val AUTO_LANGUAGE_CODE = "auto"

/**
 * Language codes the keyboard offers: auto, English, Arabic, then the user's system languages
 * (deduplicated, in system order). Pure so it can be unit-tested without a [android.os.LocaleList].
 */
fun keyboardLanguageCodes(systemLanguages: List<String>): List<String> {
    val codes = linkedSetOf(AUTO_LANGUAGE_CODE, "en", "ar")
    systemLanguages.map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotEmpty() && it != "und" }
        .forEach { codes.add(it) }
    return codes.toList()
}

/** Short label on the keyboard pill: "Auto" (localized by the caller) or the upper-cased code. */
fun keyboardLanguageShortLabel(code: String, autoLabel: String): String =
    if (code.equals(AUTO_LANGUAGE_CODE, ignoreCase = true)) autoLabel else code.uppercase(Locale.ROOT)

/** Picker label: the language's own name ("English", "العربية"), capitalized for its locale. */
fun keyboardLanguageDisplayName(code: String, autoLabel: String): String {
    if (code.equals(AUTO_LANGUAGE_CODE, ignoreCase = true)) return autoLabel
    val locale = Locale.forLanguageTag(code)
    val name = locale.getDisplayLanguage(locale).ifBlank { code }
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}
