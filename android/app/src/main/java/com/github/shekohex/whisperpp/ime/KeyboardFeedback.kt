package com.github.shekohex.whisperpp.ime

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.core.audio.SoundCues
import com.github.shekohex.whisperpp.data.PreferenceKeys.HAPTIC_FEEDBACK_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.SOUND_EFFECTS_ENABLED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Haptic and click-sound feedback for keyboard actions, honouring the user's feedback switches. */
class KeyboardFeedback(
    private val context: Context,
    private val scope: CoroutineScope,
    private val dataStore: DataStore<Preferences>,
    private val soundCues: SoundCues,
    private val decorView: () -> View?,
) {
    fun perform(isDelete: Boolean = false, cue: SoundCues.Cue? = null) {
        // Cues play synchronously so the start cue precedes capture.
        if (cue != null) soundCues.play(cue)
        scope.launch {
            val prefs = dataStore.data.first()
            if (prefs[HAPTIC_FEEDBACK_ENABLED] ?: true) {
                val effect = if (isDelete) HapticFeedbackConstants.KEYBOARD_RELEASE else HapticFeedbackConstants.KEYBOARD_TAP
                decorView()?.performHapticFeedback(effect)
            }
            if (cue == null && (prefs[SOUND_EFFECTS_ENABLED] ?: true)) {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.playSoundEffect(if (isDelete) AudioManager.FX_KEYPRESS_DELETE else AudioManager.FX_KEYPRESS_STANDARD)
            }
        }
    }
}
