package com.github.shekohex.whisperpp.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.util.Log
import androidx.annotation.RawRes
import com.github.shekohex.whisperpp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Preloaded recording cues played through [SoundPool] as sonification (E-02). [play] never blocks
 * and is a no-op when sound effects are disabled or the ringer is silent/vibrate.
 */
class SoundCues(
    context: Context,
    scope: CoroutineScope,
    soundEffectsEnabled: Flow<Boolean>,
) {
    enum class Cue(@param:RawRes val resId: Int) {
        START(R.raw.rec_start),
        STOP(R.raw.rec_stop),
        PAUSE(R.raw.rec_pause),
        DONE(R.raw.rec_done),
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val loadedIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val soundIds: Map<Cue, Int>

    @Volatile private var enabled = true
    @Volatile private var released = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedIds.add(sampleId) else Log.w(TAG, "Failed to load cue $sampleId")
        }
        soundIds = Cue.entries.associateWith { cue -> soundPool.load(context, cue.resId, 1) }
        scope.launch { soundEffectsEnabled.collect { enabled = it } }
    }

    fun play(cue: Cue) {
        if (released || !enabled) return
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val soundId = soundIds[cue] ?: return
        if (soundId !in loadedIds) return
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        if (released) return
        released = true
        soundPool.release()
    }

    companion object {
        private const val TAG = "SoundCues"
        private const val MAX_STREAMS = 2
    }
}
