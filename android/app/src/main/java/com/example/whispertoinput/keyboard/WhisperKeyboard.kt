/*
 * This file is part of Whisper To Input, see <https://github.com/j3soon/whisper-to-input>.
 *
 * Copyright (c) 2023-2025 Yan-Bin Diau, Johnson Sun
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.whispertoinput.keyboard

import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.math.MathUtils
import com.example.whispertoinput.R
import kotlin.math.log10

private const val AMPLITUDE_CLAMP_MIN: Int = 10
private const val AMPLITUDE_CLAMP_MAX: Int = 25000
private const val LOG_10_10: Float = 1.0F
private const val LOG_10_25000: Float = 4.398F

private const val BAR_MIN_HEIGHT_DP = 4
private const val BAR_MAX_HEIGHT_DP = 24

private const val INITIAL_BACKSPACE_DELAY = 400L
private const val MIN_BACKSPACE_DELAY = 50L
private const val BACKSPACE_ACCELERATION = 0.8f

enum class KeyboardState {
    Ready,
    Recording,
    Paused,
    Transcribing,
    SmartFixing,
}

class WhisperKeyboard {

    private var onMicAction: () -> Unit = { }
    private var onCancelAction: () -> Unit = { }
    private var onSendAction: () -> Unit = { }
    private var onDeleteAction: () -> Unit = { }
    private var onSwitchIme: () -> Unit = { }
    private var onOpenSettings: (View) -> Unit = { }
    private var onLanguageClick: (View) -> Unit = { }

    private var keyboardState: KeyboardState = KeyboardState.Ready

    private var keyboardView: ConstraintLayout? = null
    private var buttonMic: ImageButton? = null
    private var buttonSend: ImageButton? = null
    private var buttonCancel: ImageButton? = null
    private var buttonBackspace: ImageButton? = null
    private var labelStatus: TextView? = null
    private var labelLanguage: TextView? = null
    private var languageChip: View? = null
    private var voiceWave: LinearLayout? = null
    private var waveBars: List<View> = emptyList()
    private var waitingIcon: ProgressBar? = null
    private var buttonPreviousIme: ImageButton? = null
    private var buttonSettings: ImageButton? = null
    private var leftCluster: LinearLayout? = null
    private var rightCluster: LinearLayout? = null
    private var micFrame: View? = null
    private var density: Float = 1f

    private val handler = Handler(Looper.getMainLooper())
    private var animationRunnable: Runnable? = null
    private val glyphs = listOf("·", "✻", "✽", "✶", "✳", "✢")
    private val whimsicalWords = listOf(
        "Polishing", "Refining", "Synthesizing", "De-umm-ing", "Structuring",
        "Articulating", "Grammarizing", "Decrypting", "Unscrambling", "Enhancing",
        "Clarifying", "Flowing", "Harmonizing", "Sculpting", "Buffering",
        "Calibrating", "Quantizing", "Optimizing", "Perfecting", "Smoothing",
        "Fine-tuning", "Whisking", "Brewing", "Distilling", "Crystallizing",
        "Arranging", "Assembling", "Composing", "Editing", "Curating",
        "Revising", "Updating", "Transforming", "Converting", "Translating",
        "Deciphering", "Interpreting", "Parsing", "Processing", "Computing",
        "Calculating", "Generating", "Producing", "Creating", "Drafting",
        "Formulating", "Devising", "Developing", "Constructing", "Building",
        "Crafting", "Designing", "Modeling", "Visualizing", "Imagining",
        "Thinking", "Pondering", "Musing", "Reflecting", "Contemplating",
        "De-mumbling", "Filtering noise", "Echo-locating", "Decoding thought",
        "Tuning frequencies", "Normalizing vibes", "Resonating", "Audio-magic",
        "Crystal clear", "Word-smithing", "Text-weaving", "Sentence-forming",
        "Vocal-mining", "Data-harvesting", "Neural-mapping", "Semantic-linking",
        "Logical-chaining", "Context-fixing", "Syntax-checking", "Typo-hunting",
        "Comma-placing", "Verb-squashing", "Noun-polishing", "Adverb-cleaning",
        "Pronoun-swapping", "Active-voicing", "Passive-killing", "Cliché-cutting",
        "Meaning-extraction", "Idea-crystallizing", "Thought-shaping", "Voice-lifting",
        "Silence-cutting", "Breath-removal", "Filler-vacuuming", "Stutter-fixing",
        "Accent-neutralizing", "Dialect-mapping", "Lexicon-scanning", "Thesaurus-sync",
        "Tone-matching", "Style-copying", "User-mimicry", "Shadow-typing",
        "Ghost-writing", "Digital-scribing", "Cyber-transcribing", "Quantum-fixing",
        "Atomic-refining", "Deep-listening", "Active-hearing", "Smart-sorting",
        "Wise-picking", "Elite-editing", "Expert-polishing", "Master-crafting",
        "Punctuation-pro", "Logic-lapping", "Flow-finding", "Meaning-matching",
        "Intent-tracking", "Speech-spinning", "Vocal-polishing", "Audio-cleaning",
        "Wave-watching", "Sound-shaping", "Nuance-noting", "Clarity-calling",
        "Style-stitching", "Tone-tuning", "Voice-validating", "Text-taming",
        "Grammar-guarding", "Syntax-saving", "Word-watching", "Error-erasing",
        "Mumble-mending", "Pause-purging", "Filler-filtering", "Static-stripping",
        "Crisp-coding", "Draft-distilling", "Acoustic-aiming", "Signal-strengthening",
        "Data-dreaming", "Pattern-parsing", "Cluster-cleaning", "Link-leveling",
        "Concept-carving", "Abstract-aligning", "Reality-rendering", "Input-igniting",
        "Output-optimizing", "Smart-smoothing", "Fast-fixing", "Deep-diving",
        "Surface-shining", "Core-correcting", "Base-balancing", "Top-tuning",
        "Level-lifting", "Peak-polishing", "Vibe-validating", "Spirit-syncing"
    )

    private var backspaceRunnable: Runnable? = null
    private var currentBackspaceDelay = INITIAL_BACKSPACE_DELAY

    fun setup(
        layoutInflater: LayoutInflater,
        shouldOfferImeSwitch: Boolean,
        onMicAction: () -> Unit,
        onCancelAction: () -> Unit,
        onSendAction: () -> Unit,
        onDeleteAction: () -> Unit,
        onSwitchIme: () -> Unit,
        onOpenSettings: (View) -> Unit,
        onLanguageClick: (View) -> Unit,
    ): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as ConstraintLayout
        density = keyboardView!!.resources.displayMetrics.density

        buttonMic = keyboardView!!.findViewById(R.id.btn_mic) as ImageButton
        buttonSend = keyboardView!!.findViewById(R.id.btn_send) as ImageButton
        buttonCancel = keyboardView!!.findViewById(R.id.btn_cancel) as ImageButton
        buttonBackspace = keyboardView!!.findViewById(R.id.btn_backspace) as ImageButton
        labelStatus = keyboardView!!.findViewById(R.id.label_status) as TextView
        labelLanguage = keyboardView!!.findViewById(R.id.label_language) as TextView
        languageChip = keyboardView!!.findViewById(R.id.language_chip)
        voiceWave = keyboardView!!.findViewById(R.id.voice_wave) as LinearLayout
        waitingIcon = keyboardView!!.findViewById(R.id.pb_waiting_icon) as ProgressBar
        buttonPreviousIme = keyboardView!!.findViewById(R.id.btn_previous_ime) as ImageButton
        buttonSettings = keyboardView!!.findViewById(R.id.btn_settings) as ImageButton
        leftCluster = keyboardView!!.findViewById(R.id.left_cluster) as LinearLayout
        rightCluster = keyboardView!!.findViewById(R.id.right_cluster) as LinearLayout
        micFrame = keyboardView!!.findViewById(R.id.btn_mic_frame)
        waveBars = listOf(
            keyboardView!!.findViewById(R.id.voice_wave_bar_0),
            keyboardView!!.findViewById(R.id.voice_wave_bar_1),
            keyboardView!!.findViewById(R.id.voice_wave_bar_2),
            keyboardView!!.findViewById(R.id.voice_wave_bar_3),
            keyboardView!!.findViewById(R.id.voice_wave_bar_4)
        )

        if (!shouldOfferImeSwitch) {
            buttonPreviousIme!!.visibility = View.GONE
        }

        buttonMic!!.setOnClickListener { onButtonMicClick() }
        buttonSend!!.setOnClickListener { onButtonSendClick() }
        buttonCancel!!.setOnClickListener { onButtonCancelClick() }
        buttonSettings!!.setOnClickListener { onButtonSettingsClick() }
        languageChip!!.setOnClickListener { onLanguageClick(it) }

        setupBackspaceLongPress()

        if (shouldOfferImeSwitch) {
            buttonPreviousIme!!.setOnClickListener { onButtonPreviousImeClick() }
        }

        this.onMicAction = onMicAction
        this.onCancelAction = onCancelAction
        this.onSendAction = onSendAction
        this.onDeleteAction = onDeleteAction
        this.onSwitchIme = onSwitchIme
        this.onOpenSettings = onOpenSettings
        this.onLanguageClick = onLanguageClick

        reset()

        return keyboardView!!
    }

    private fun setupBackspaceLongPress() {
        backspaceRunnable = object : Runnable {
            override fun run() {
                onDeleteAction()
                currentBackspaceDelay = (currentBackspaceDelay * BACKSPACE_ACCELERATION).toLong()
                    .coerceAtLeast(MIN_BACKSPACE_DELAY)
                handler.postDelayed(this, currentBackspaceDelay)
            }
        }

        buttonBackspace!!.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    onDeleteAction()
                    currentBackspaceDelay = INITIAL_BACKSPACE_DELAY
                    handler.postDelayed(backspaceRunnable!!, currentBackspaceDelay)
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(backspaceRunnable!!)
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    fun reset() {
        render(KeyboardState.Ready)
    }

    fun setLanguageLabel(label: String) {
        labelLanguage?.text = label
    }

    fun updateMicrophoneAmplitude(amplitude: Int) {
        if (keyboardState != KeyboardState.Recording) {
            return
        }

        val clampedAmplitude = MathUtils.clamp(
            amplitude,
            AMPLITUDE_CLAMP_MIN,
            AMPLITUDE_CLAMP_MAX
        )

        val normalizedPower =
            (log10(clampedAmplitude * 1f) - LOG_10_10) / (LOG_10_25000 - LOG_10_10)

        val heightRange = BAR_MAX_HEIGHT_DP - BAR_MIN_HEIGHT_DP
        val waveMultipliers = floatArrayOf(0.5f, 0.8f, 1.0f, 0.8f, 0.5f)

        waveBars.forEachIndexed { index, bar ->
            val barHeight = BAR_MIN_HEIGHT_DP + (heightRange * normalizedPower * waveMultipliers[index])
            val heightPx = (barHeight * density).toInt()
            val params = bar.layoutParams
            params.height = heightPx
            bar.layoutParams = params
        }
    }

    private fun resetWaveBars() {
        val minHeightPx = (BAR_MIN_HEIGHT_DP * density).toInt()
        waveBars.forEach { bar ->
            val params = bar.layoutParams
            params.height = minHeightPx
            bar.layoutParams = params
        }
    }

    private fun animateMicFrame(scale: Float) {
        micFrame?.animate()?.cancel()
        micFrame?.animate()
            ?.scaleX(scale)
            ?.scaleY(scale)
            ?.setDuration(200)
            ?.start()
    }

    private fun onButtonPreviousImeClick() {
        this.onSwitchIme()
    }

    private fun onButtonSettingsClick() {
        val anchor = buttonSettings ?: return
        this.onOpenSettings(anchor)
    }

    private fun onButtonMicClick() {
        onMicAction()
    }

    private fun onButtonSendClick() {
        onSendAction()
    }

    private fun onButtonCancelClick() {
        onCancelAction()
    }

    private fun stopAnimation() {
        animationRunnable?.let { handler.removeCallbacks(it) }
        animationRunnable = null
        labelStatus?.clearAnimation()
        labelStatus?.alpha = 1f
    }

    fun render(state: KeyboardState) {
        stopAnimation()
        val defaultTextColor = labelStatus!!.textColors.defaultColor
        
        when (state) {
            KeyboardState.Ready -> {
                labelStatus!!.setText(R.string.input_ready)
                labelStatus!!.visibility = View.VISIBLE
                labelStatus!!.setTextColor(defaultTextColor)
                labelStatus!!.typeface = Typeface.DEFAULT_BOLD
                voiceWave!!.visibility = View.GONE
                buttonMic!!.setImageResource(R.drawable.ic_mic)
                waitingIcon!!.visibility = View.INVISIBLE
                buttonSend!!.visibility = View.GONE
                buttonCancel!!.visibility = View.INVISIBLE
                buttonBackspace!!.visibility = View.VISIBLE
                resetWaveBars()
                animateMicFrame(1f)
                keyboardView!!.keepScreenOn = false
            }

            KeyboardState.Recording -> {
                labelStatus!!.setText(R.string.input_recording)
                labelStatus!!.visibility = View.INVISIBLE
                voiceWave!!.visibility = View.VISIBLE
                buttonMic!!.setImageResource(R.drawable.ic_pause)
                waitingIcon!!.visibility = View.INVISIBLE
                buttonSend!!.visibility = View.GONE
                buttonCancel!!.visibility = View.VISIBLE
                buttonBackspace!!.visibility = View.GONE
                resetWaveBars()
                animateMicFrame(1.05f)
                keyboardView!!.keepScreenOn = true
            }

            KeyboardState.Paused -> {
                labelStatus!!.setText(R.string.input_paused)
                labelStatus!!.visibility = View.VISIBLE
                labelStatus!!.setTextColor(0xFFFFD600.toInt()) // Yellowish
                labelStatus!!.typeface = Typeface.DEFAULT
                voiceWave!!.visibility = View.GONE
                buttonMic!!.setImageResource(R.drawable.ic_play_circle)
                waitingIcon!!.visibility = View.INVISIBLE
                buttonSend!!.visibility = View.VISIBLE
                buttonCancel!!.visibility = View.VISIBLE
                buttonBackspace!!.visibility = View.GONE
                resetWaveBars()
                animateMicFrame(1f)
                keyboardView!!.keepScreenOn = true
                
                val pulse = AlphaAnimation(0.3f, 1f)
                pulse.duration = 800
                pulse.repeatMode = Animation.REVERSE
                pulse.repeatCount = Animation.INFINITE
                labelStatus!!.startAnimation(pulse)
            }

            KeyboardState.Transcribing -> {
                labelStatus!!.visibility = View.VISIBLE
                labelStatus!!.setTextColor(0xFF1976D2.toInt()) // Blueish
                labelStatus!!.typeface = Typeface.DEFAULT
                voiceWave!!.visibility = View.GONE
                buttonMic!!.setImageResource(R.drawable.ic_settings_voice)
                waitingIcon!!.visibility = View.VISIBLE
                buttonSend!!.visibility = View.GONE
                buttonCancel!!.visibility = View.VISIBLE
                buttonBackspace!!.visibility = View.GONE
                resetWaveBars()
                animateMicFrame(1f)
                keyboardView!!.keepScreenOn = true
                
                val baseText = keyboardView!!.context.getString(R.string.input_transcribing)
                var dots = 0
                animationRunnable = object : Runnable {
                    override fun run() {
                        dots = (dots + 1) % 4
                        val text = baseText + ".".repeat(dots)
                        labelStatus!!.text = text
                        handler.postDelayed(this, 500)
                    }
                }
                handler.post(animationRunnable!!)
            }

            KeyboardState.SmartFixing -> {
                labelStatus!!.visibility = View.VISIBLE
                labelStatus!!.setTextColor(0xFF388E3C.toInt()) // Greenish
                labelStatus!!.typeface = Typeface.DEFAULT
                voiceWave!!.visibility = View.GONE
                buttonMic!!.setImageResource(R.drawable.ic_settings_voice)
                waitingIcon!!.visibility = View.VISIBLE
                buttonSend!!.visibility = View.GONE
                buttonCancel!!.visibility = View.VISIBLE
                buttonBackspace!!.visibility = View.GONE
                resetWaveBars()
                animateMicFrame(1f)
                keyboardView!!.keepScreenOn = true
                
                var glyphIndex = 0
                var currentWord = whimsicalWords.random()
                var lastWordChangeTime = 0L
                val wordChangeInterval = 2500L
                val glyphChangeInterval = 100L // ~10fps

                animationRunnable = object : Runnable {
                    override fun run() {
                        val currentTime = System.currentTimeMillis()
                        
                        // Change word every 2500ms
                        if (currentTime - lastWordChangeTime >= wordChangeInterval) {
                            currentWord = whimsicalWords.random()
                            lastWordChangeTime = currentTime
                            
                            // Slide up animation only when word changes
                            val slideUp = TranslateAnimation(0f, 0f, 20f, 0f)
                            slideUp.duration = 400 // Slightly longer slide for the slower pace
                            labelStatus!!.startAnimation(slideUp)
                        }

                        // Animate glyph at ~10fps
                        val glyph = glyphs[glyphIndex]
                        glyphIndex = (glyphIndex + 1) % glyphs.size
                        
                        labelStatus!!.text = "$glyph $currentWord"
                        
                        handler.postDelayed(this, glyphChangeInterval)
                    }
                }
                handler.post(animationRunnable!!)
            }
        }

        keyboardState = state
    }
}
