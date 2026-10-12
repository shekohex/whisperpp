package com.github.shekohex.whisperpp.ui.keyboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import com.github.shekohex.whisperpp.keyboard.isPaused
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Main record button: one gesture handler (§4.1) plus accessibility actions mapped to the same intents (H-15).
 *
 * RTL decision (WP-3.7): the bar is laid out with the locale's direction, so in RTL the record button
 * sits at the physical LEFT edge. "Slide to cancel" therefore follows the layout, not the screen: it
 * always moves toward the START side (left in LTR, right in RTL), i.e. away from the edge the button is
 * on — a physical-left gesture would run off-screen in RTL. [CancelHint]'s auto-mirrored arrow and
 * travel direction match this; "slide up to lock" is direction-neutral.
 */
@Composable
fun MainActionButton(
    state: KeyboardState,
    externalSendingBlocked: Boolean,
    showLongPressHint: Boolean,
    tapToRecord: Boolean,
    isStarting: Boolean,
    onIntent: (RecordIntent) -> Unit,
    onUi: (GestureUi) -> Unit,
    onDismissHint: () -> Unit,
    onBlockedAction: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentState by rememberUpdatedState(state)
    val currentBlocked by rememberUpdatedState(externalSendingBlocked)
    val currentTapToRecord by rememberUpdatedState(tapToRecord)
    val currentIsStarting by rememberUpdatedState(isStarting)
    val currentOnIntent by rememberUpdatedState(onIntent)
    val currentOnUi by rememberUpdatedState(onUi)
    val currentOnBlockedAction by rememberUpdatedState(onBlockedAction)

    LaunchedEffect(showLongPressHint) {
        if (showLongPressHint) {
            delay(2500)
            onDismissHint()
        }
    }

    val size by animateDpAsState(
        targetValue = when (state) {
            KeyboardState.Recording -> 64.dp
            KeyboardState.RecordingLocked -> 56.dp
            else -> 48.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ButtonSize"
    )

    val containerColor by animateColorAsState(
        when {
            externalSendingBlocked -> MaterialTheme.colorScheme.surfaceVariant
            state == KeyboardState.Recording -> MaterialTheme.colorScheme.error
            state == KeyboardState.RecordingLocked -> MaterialTheme.colorScheme.tertiary
            state.isPaused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        label = "ContainerColor"
    )

    val contentColor by animateColorAsState(
        when {
            externalSendingBlocked -> MaterialTheme.colorScheme.onSurfaceVariant
            state == KeyboardState.Recording -> MaterialTheme.colorScheme.onError
            state == KeyboardState.RecordingLocked -> MaterialTheme.colorScheme.onTertiary
            state.isPaused -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        label = "ContentColor"
    )

    val icon = when {
        externalSendingBlocked -> Icons.Default.Lock
        else -> when (state) {
            KeyboardState.RecordingLocked,
            KeyboardState.Paused,
            KeyboardState.PausedLocked,
            -> Icons.AutoMirrored.Filled.Send
            KeyboardState.Ready,
            KeyboardState.Recording,
            KeyboardState.Transcribing,
            KeyboardState.Enhancing,
            -> Icons.Default.Mic
        }
    }

    val description = stringResource(
        when {
            externalSendingBlocked -> R.string.record_button_blocked
            else -> when (state) {
                KeyboardState.Ready -> if (tapToRecord) R.string.record_button_ready_tap else R.string.record_button_ready_hold
                KeyboardState.Recording -> R.string.record_button_recording
                KeyboardState.RecordingLocked -> R.string.record_button_locked
                KeyboardState.Paused, KeyboardState.PausedLocked -> R.string.record_button_paused
                KeyboardState.Transcribing, KeyboardState.Enhancing -> R.string.record_button_busy
            }
        }
    )
    val stateLabel = stringResource(
        when (state) {
            KeyboardState.Ready -> R.string.keyboard_record_state_idle
            KeyboardState.Recording -> R.string.keyboard_record_state_recording
            KeyboardState.RecordingLocked -> R.string.keyboard_record_state_locked
            KeyboardState.Paused, KeyboardState.PausedLocked -> R.string.keyboard_record_state_paused
            KeyboardState.Transcribing, KeyboardState.Enhancing -> R.string.keyboard_record_state_processing
        }
    )
    val startLabel = stringResource(R.string.record_action_start)
    val sendLabel = stringResource(R.string.record_action_send)
    val lockLabel = stringResource(R.string.record_action_lock)
    val cancelLabel = stringResource(R.string.record_action_cancel)
    val pauseLabel = stringResource(R.string.record_action_pause)
    val resumeLabel = stringResource(R.string.record_action_resume)
    // TalkBack never needs the drag: every gesture outcome is a click or a custom action.
    val a11yActions: List<Pair<String, RecordIntent>> = when (state) {
        KeyboardState.Ready -> listOf(startLabel to RecordIntent.PressStart(locked = true))
        KeyboardState.Recording -> listOf(
            sendLabel to RecordIntent.Stop,
            lockLabel to RecordIntent.Lock,
            cancelLabel to RecordIntent.Cancel,
        )
        KeyboardState.RecordingLocked -> listOf(
            sendLabel to RecordIntent.Stop,
            pauseLabel to RecordIntent.Pause,
            cancelLabel to RecordIntent.Cancel,
        )
        KeyboardState.Paused, KeyboardState.PausedLocked -> listOf(
            sendLabel to RecordIntent.Stop,
            resumeLabel to RecordIntent.Resume,
            cancelLabel to RecordIntent.Cancel,
        )
        KeyboardState.Transcribing, KeyboardState.Enhancing -> emptyList()
    }
    val a11yModifier = Modifier.semantics(mergeDescendants = true) {
        contentDescription = description
        stateDescription = stateLabel
        role = Role.Button
        if (externalSendingBlocked) {
            onClick { currentOnBlockedAction(); true }
            return@semantics
        }
        a11yActions.firstOrNull()?.let { (label, intent) ->
            onClick(label = label) { currentOnIntent(intent); true }
        }
        customActions = a11yActions.map { (label, intent) ->
            CustomAccessibilityAction(label) { currentOnIntent(intent); true }
        }
    }

    val gestureModifier = Modifier.pointerInput(isRtl) {
        val machine = RecordGestureStateMachine(
            density = density,
            touchSlopPx = viewConfiguration.touchSlop,
            tapToRecord = { currentTapToRecord },
            startIsLeft = !isRtl,
        )
        val dispatch: (GestureInput) -> Unit = { input ->
            val output = machine.reduce(input, currentState.toRecordSession(currentIsStarting))
            output.intents.forEach { intent ->
                if (intent is RecordIntent.Haptic) {
                    haptic.performHapticFeedback(intent.kind.toFeedbackType())
                } else {
                    currentOnIntent(intent)
                }
            }
            currentOnUi(output.ui)
        }
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            if (currentBlocked) {
                if (waitForUpOrCancellation() != null) currentOnBlockedAction()
                return@awaitEachGesture
            }
            dispatch(GestureInput.Down(down.uptimeMillis))
            var ended = false
            try {
                val endedBeforeHold = withTimeoutOrNull(GestureTokens.HOLD_THRESHOLD_MS) {
                    trackRecordPointer(down, dispatch)
                }
                if (endedBeforeHold == null) {
                    dispatch(GestureInput.HoldTimerFired)
                    trackRecordPointer(down, dispatch)
                }
                ended = true
            } finally {
                // The input view went away mid-gesture (window hidden / recomposed out).
                if (!ended) dispatch(GestureInput.Cancel)
            }
        }
    }

    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(containerColor)
                .then(a11yModifier)
                .then(gestureModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(when (state) {
                    KeyboardState.Recording -> 32.dp
                    KeyboardState.RecordingLocked -> 28.dp
                    else -> 24.dp
                })
            )
        }
    }
}

@Composable
fun BackspaceButton(onDeleteAction: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            onDeleteAction()
            delay(500L)
            var currentDelay = 100L
            while (isPressed) {
                onDeleteAction()
                delay(currentDelay)
                currentDelay = (currentDelay * 0.9f).toLong().coerceAtLeast(40L)
            }
        }
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    isPressed = true
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        isPressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Backspace,
            contentDescription = stringResource(R.string.backspace_button_hint),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun WhimsicalStatus() {
    val whimsicalWords = stringArrayResource(R.array.keyboard_enhancing_words)
    val glyphs = listOf("·", "✻", "✽", "✶", "✳", "✢")
    
    var word by remember { mutableStateOf(whimsicalWords.random()) }
    var glyphIndex by remember { mutableIntStateOf(0) }
    val glyph = glyphs[glyphIndex]
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(word) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2500)
            word = whimsicalWords.random()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            glyphIndex = (glyphIndex + 1) % glyphs.size
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "WhimsicalShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WhimsicalShimmerOffset"
    )
    
    val baseColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(shimmerOffset * 400f - 100f, 0f),
        end = Offset(shimmerOffset * 400f + 100f, 0f)
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(glyph, color = MaterialTheme.colorScheme.tertiary, fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = word,
            style = MaterialTheme.typography.bodyMedium.copy(brush = brush)
        )
    }
}

fun Modifier.animateAlphaLoop(): Modifier = this.composed {
    val infiniteTransition = rememberInfiniteTransition(label = "AlphaLoop")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaValue"
    )
    this.then(Modifier.graphicsLayer { this.alpha = alpha })
}
