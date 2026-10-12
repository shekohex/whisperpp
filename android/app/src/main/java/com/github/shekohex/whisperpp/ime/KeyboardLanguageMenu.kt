package com.github.shekohex.whisperpp.ime

import android.content.Context
import android.os.LocaleList
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.PreferenceKeys.LANGUAGE_CODE
import com.github.shekohex.whisperpp.ui.keyboard.KeyboardLanguageOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The keyboard's language chip and menu: the stored language, its short label and the pickable options. */
class KeyboardLanguageMenu(
    private val context: Context,
    private val scope: CoroutineScope,
    private val dataStore: DataStore<Preferences>,
) {
    private val _label = mutableStateOf("")
    private val _selected = mutableStateOf(AUTO_LANGUAGE_CODE)
    private val _options = mutableStateOf<List<KeyboardLanguageOption>>(emptyList())

    val label: State<String> = _label
    val selected: State<String> = _selected
    val options: State<List<KeyboardLanguageOption>> = _options

    fun refresh() {
        scope.launch {
            val languageCode = dataStore.data.map { it[LANGUAGE_CODE] ?: AUTO_LANGUAGE_CODE }.first()
            val autoLabel = context.getString(R.string.language_auto_label)
            _selected.value = languageCode
            _label.value = keyboardLanguageShortLabel(languageCode, autoLabel)
            val systemLocales = LocaleList.getDefault()
            val systemLanguages = (0 until systemLocales.size()).map { systemLocales.get(it).language }
            _options.value = keyboardLanguageCodes(systemLanguages).map { code ->
                KeyboardLanguageOption(code, keyboardLanguageDisplayName(code, autoLabel))
            }
        }
    }

    fun select(code: String) {
        scope.launch {
            dataStore.edit { it[LANGUAGE_CODE] = code }
            refresh()
        }
    }
}
