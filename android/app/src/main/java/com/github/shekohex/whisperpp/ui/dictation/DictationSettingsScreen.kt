package com.github.shekohex.whisperpp.ui.dictation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.PreferenceKeys.ADD_TRAILING_SPACE
import com.github.shekohex.whisperpp.data.PreferenceKeys.AUTO_RECORDING_START
import com.github.shekohex.whisperpp.data.PreferenceKeys.AUTO_SEND_AFTER_INSERT
import com.github.shekohex.whisperpp.data.PreferenceKeys.AUTO_SWITCH_BACK
import com.github.shekohex.whisperpp.data.PreferenceKeys.AUTO_TRANSCRIBE_ON_PAUSE
import com.github.shekohex.whisperpp.data.PreferenceKeys.CANCEL_CONFIRMATION
import com.github.shekohex.whisperpp.data.PreferenceKeys.FILTER_FILLERS_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.FORMAT_PARAGRAPHS_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.HAPTIC_FEEDBACK_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.LANGUAGE_CODE
import com.github.shekohex.whisperpp.data.PreferenceKeys.PAUSE_MEDIA_WHILE_RECORDING
import com.github.shekohex.whisperpp.data.PreferenceKeys.REALTIME_STT_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.SOUND_EFFECTS_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.TAP_TO_RECORD
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.components.SectionCard
import com.github.shekohex.whisperpp.ui.components.SettingsSwitchRow
import com.github.shekohex.whisperpp.ui.settings.common.SettingsDropdown
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.commonLanguages
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.ui.settings.common.languageLabel

/** Language, recording, insertion and formatting behavior of the keyboard (old "Keyboard" screen). */
@Composable
fun DictationSettingsScreen(
    onBack: () -> Unit,
    viewModel: DictationSettingsViewModel = containerViewModel { container, _ -> DictationSettingsViewModel(container) },
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    @Composable
    fun Toggle(icon: ImageVector, title: String, key: Preferences.Key<Boolean>, default: Boolean) = SettingsSwitchRow(
        leadingIcon = icon,
        title = title,
        checked = prefs?.get(key) ?: default,
        onCheckedChange = { viewModel.set(key, it) },
    )

    SettingsPage(
        title = stringResource(R.string.dictation_settings_title),
        intro = stringResource(R.string.dictation_settings_intro),
        onBack = onBack,
    ) {
        SectionCard(title = stringResource(R.string.dictation_group_language)) {
            SettingsDropdown(
                label = stringResource(R.string.dictation_language_label),
                value = languageLabel(prefs?.get(LANGUAGE_CODE) ?: "auto"),
                options = commonLanguages(),
                optionLabel = { it.second },
                onSelect = { viewModel.set(LANGUAGE_CODE, it.first) },
                modifier = Modifier.padding(16.dp),
            )
            Toggle(Icons.Default.GraphicEq, stringResource(R.string.settings_realtime_stt_enabled), REALTIME_STT_ENABLED, true)
        }
        SectionCard(title = stringResource(R.string.dictation_group_automation)) {
            Toggle(Icons.Default.Mic, stringResource(R.string.dictation_auto_start), AUTO_RECORDING_START, true)
            Toggle(Icons.Default.TouchApp, stringResource(R.string.settings_tap_to_record), TAP_TO_RECORD, true)
            Toggle(Icons.Default.SwapHoriz, stringResource(R.string.dictation_auto_switch_back), AUTO_SWITCH_BACK, false)
            Toggle(
                Icons.AutoMirrored.Filled.Send,
                stringResource(R.string.dictation_auto_transcribe),
                AUTO_TRANSCRIBE_ON_PAUSE,
                true,
            )
        }
        SectionCard(title = stringResource(R.string.dictation_group_extras)) {
            Toggle(Icons.Default.SpaceBar, stringResource(R.string.dictation_trailing_space), ADD_TRAILING_SPACE, false)
            Toggle(
                Icons.Default.QuestionMark,
                stringResource(R.string.dictation_cancel_confirmation),
                CANCEL_CONFIRMATION,
                true,
            )
        }
        SectionCard(title = stringResource(R.string.settings_group_dictation)) {
            Toggle(Icons.Default.CleaningServices, stringResource(R.string.settings_filter_fillers), FILTER_FILLERS_ENABLED, false)
            Toggle(Icons.Default.ViewStream, stringResource(R.string.settings_format_paragraphs), FORMAT_PARAGRAPHS_ENABLED, false)
            Toggle(Icons.AutoMirrored.Filled.Send, stringResource(R.string.settings_auto_send), AUTO_SEND_AFTER_INSERT, false)
        }
    }
}

/** Sounds, haptics and pausing other media while recording (§4.8 "Audio"). */
@Composable
fun AudioSettingsScreen(
    onBack: () -> Unit,
    viewModel: DictationSettingsViewModel = containerViewModel { container, _ -> DictationSettingsViewModel(container) },
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    @Composable
    fun Toggle(icon: ImageVector, title: String, key: Preferences.Key<Boolean>, default: Boolean) = SettingsSwitchRow(
        leadingIcon = icon,
        title = title,
        checked = prefs?.get(key) ?: default,
        onCheckedChange = { viewModel.set(key, it) },
    )

    SettingsPage(
        title = stringResource(R.string.audio_settings_title),
        intro = stringResource(R.string.audio_settings_intro),
        onBack = onBack,
    ) {
        SectionCard(title = stringResource(R.string.dictation_audio_group_feedback)) {
            Toggle(Icons.Default.Vibration, stringResource(R.string.dictation_audio_haptics), HAPTIC_FEEDBACK_ENABLED, true)
            Toggle(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.dictation_audio_sounds), SOUND_EFFECTS_ENABLED, true)
            Toggle(Icons.Default.MusicOff, stringResource(R.string.settings_pause_media_while_recording), PAUSE_MEDIA_WHILE_RECORDING, true)
        }
    }
}

@Composable
private fun SettingsPage(
    title: String,
    intro: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    PageScaffold(
        title = title,
        onBack = onBack,
        actions = { SettingsHelpAction(SettingsHelpTopic.DICTATION) },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = intro,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
