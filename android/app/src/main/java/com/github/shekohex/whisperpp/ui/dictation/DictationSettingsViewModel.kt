package com.github.shekohex.whisperpp.ui.dictation

import com.github.shekohex.whisperpp.di.AppContainer
import com.github.shekohex.whisperpp.ui.settings.common.PreferencesViewModel

/** Dictation and audio toggles; each is a single preference key written immediately. */
class DictationSettingsViewModel(container: AppContainer) : PreferencesViewModel(container.settingsDataStore)
