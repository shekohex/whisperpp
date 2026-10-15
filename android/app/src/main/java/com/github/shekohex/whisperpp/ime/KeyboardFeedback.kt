package com.github.shekohex.whisperpp.ime

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.core.audio.SoundCues
import com.github.shekohex.whisperpp.data.PreferenceKeys.HAPTIC_FEEDBACK_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.SOUND_EFFECTS_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.STREAMING_ENHANCEMENT_HAPTICS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Haptic and click-sound feedback for keyboard actions, honouring the user's feedback switches. */
class KeyboardFeedback(
    private val context: Context,
    private val scope: CoroutineScope,
    private val dataStore: DataStore<Preferences>,
    private val soundCues: SoundCues,
    private val decorView: () -> View?,
) {
    private val streamingHapticsEnabled = dataStore.data.map { prefs ->
        (prefs[HAPTIC_FEEDBACK_ENABLED] ?: true) && (prefs[STREAMING_ENHANCEMENT_HAPTICS] ?: false)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    fun performStreamingTick() {
        if (!streamingHapticsEnabled.value) return
        val view = decorView()?.takeIf { it.isAttachedToWindow && it.isShown } ?: return
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        })
    }

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
