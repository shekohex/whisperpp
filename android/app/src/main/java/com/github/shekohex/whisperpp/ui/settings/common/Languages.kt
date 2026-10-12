package com.github.shekohex.whisperpp.ui.settings.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import com.github.shekohex.whisperpp.R
import java.util.Locale

/** Code that lets the provider detect the spoken language. */
const val AUTO_LANGUAGE_CODE = "auto"

/** Language codes offered in settings pickers ([AUTO_LANGUAGE_CODE] lets the provider detect). */
val COMMON_LANGUAGE_CODES: List<String> = listOf(
    AUTO_LANGUAGE_CODE,
    "en",
    "ar",
    "zh",
    "es",
    "fr",
    "de",
    "ja",
    "ko",
    "ru",
)

/** `code to label` pairs for [COMMON_LANGUAGE_CODES], labelled in the current UI language. */
@Composable
fun commonLanguages(): List<Pair<String, String>> {
    val autoLabel = stringResource(R.string.settings_language_auto)
    val locale = currentUiLocale()
    return remember(autoLabel, locale) {
        COMMON_LANGUAGE_CODES.map { code -> code to languageDisplayName(code, autoLabel, locale) }
    }
}

/** Label for a language [code] in the current UI language; unknown codes are shown as-is. */
@Composable
fun languageLabel(code: String): String =
    languageDisplayName(code, stringResource(R.string.settings_language_auto), currentUiLocale())

@Composable
private fun currentUiLocale(): Locale =
    ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT

private fun languageDisplayName(code: String, autoLabel: String, uiLocale: Locale): String {
    if (code == AUTO_LANGUAGE_CODE) return autoLabel
    val name = Locale.forLanguageTag(code).getDisplayLanguage(uiLocale)
    if (name.isBlank() || name == code) return code
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(uiLocale) else it.toString() }
}
