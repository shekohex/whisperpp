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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun KeyboardScreen(
    state: KeyboardState,
    languageLabel: String,
    amplitude: Int,
    recordingTimeMs: Long,
    showLongPressHint: Boolean,
    onMicAction: () -> Unit,
    onCancelAction: () -> Unit,
    onDiscardAction: () -> Unit,
    onSendAction: () -> Unit,
    onDeleteAction: () -> Unit,
    onOpenSettings: () -> Unit,
    onLanguageClick: () -> Unit,
    onDismissHint: () -> Unit,
    onLockAction: () -> Unit = {},
    onUnlockAction: () -> Unit = {}
) {
    var swipeProgress by remember { mutableFloatStateOf(0f) }
    var swipeUpProgress by remember { mutableFloatStateOf(0f) }
    val isSwiping = swipeProgress > 0.05f
    val isSwipingUp = swipeUpProgress > 0.05f
    val isRecordingState = state == KeyboardState.Recording || state == KeyboardState.RecordingLocked
    
    LaunchedEffect(state) {
        if (!isRecordingState) {
            swipeProgress = 0f
            swipeUpProgress = 0f
        }
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 15.dp)
            .height(72.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Cluster
            Box(modifier = Modifier.width(100.dp), contentAlignment = Alignment.CenterStart) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSwiping && isRecordingState,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    PulsatingTrashIcon(swipeProgress)
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isSwiping || !isRecordingState,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    AnimatedContent(
                        targetState = state,
                        transitionSpec = {
                            fadeIn() + slideInHorizontally() togetherWith fadeOut() + slideOutHorizontally()
                        },
                        label = "LeftClusterTransition"
                    ) { targetState ->
                        when (targetState) {
                            KeyboardState.Recording, KeyboardState.RecordingLocked -> {
                                RecordingTimer(recordingTimeMs)
                            }
                            KeyboardState.Paused -> {
                                IconButton(onClick = onCancelAction) {
                                    Icon(
                                        Icons.Default.Delete,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            else -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = onOpenSettings) {
                                        Icon(
                                            Icons.Default.Settings,
                                            null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        onClick = onLanguageClick,
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = languageLabel,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Center Status
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSwiping && isRecordingState,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    ShimmerText(
                        text = stringResource(R.string.swipe_to_discard),
                        progress = swipeProgress
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSwipingUp && state == KeyboardState.Recording && !isSwiping,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    ShimmerLockText(progress = swipeUpProgress)
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isSwiping && !isSwipingUp || !isRecordingState,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    StatusContent(state, amplitude, recordingTimeMs)
                }
            }

            // Right Cluster
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(visible = state == KeyboardState.Ready) {
                    BackspaceButton(onDeleteAction)
                }

                AnimatedVisibility(visible = state == KeyboardState.Transcribing || state == KeyboardState.SmartFixing) {
                    IconButton(onClick = onCancelAction) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.width(8.dp))
                
                MainActionButton(
                    state = state,
                    showLongPressHint = showLongPressHint,
                    onSwipeProgress = { swipeProgress = it },
                    onSwipeUpProgress = { swipeUpProgress = it },
                    onMicAction = onMicAction,
                    onDiscardAction = onDiscardAction,
                    onSendAction = onSendAction,
                    onDismissHint = onDismissHint,
                    onLockAction = onLockAction,
                    onUnlockAction = onUnlockAction
                )
            }
        }
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    val ms = (millis % 1000) / 10
    return "%02d:%02d.%02d".format(mins, secs, ms)
}

@Composable
fun RecordingTimer(millis: Long) {
    val infiniteTransition = rememberInfiniteTransition(label = "RecDot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotAlpha"
    )
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = dotAlpha))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = formatTime(millis),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StatusContent(state: KeyboardState, amplitude: Int, recordingTimeMs: Long) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
        },
        label = "KeyboardStateTransition"
    ) { targetState ->
        when (targetState) {
            KeyboardState.Ready -> {
                Text(
                    stringResource(R.string.input_ready),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            KeyboardState.Recording -> {
                VoiceWaveform(amplitude)
            }
            KeyboardState.RecordingLocked -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    VoiceWaveform(amplitude)
                }
            }
            KeyboardState.Paused -> {
                Text(
                    text = formatTime(recordingTimeMs),
                    color = Color(0xFFFFD600),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.animateAlphaLoop()
                )
            }
            KeyboardState.Transcribing -> {
                TranscribingStatus()
            }
            KeyboardState.SmartFixing -> {
                WhimsicalStatus()
            }
        }
    }
}

@Composable
fun TranscribingStatus() {
    val infiniteTransition = rememberInfiniteTransition(label = "TranscribingShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "TranscribingShimmerOffset"
    )
    
    val baseColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.primary
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(shimmerOffset * 400f - 100f, 0f),
        end = Offset(shimmerOffset * 400f + 100f, 0f)
    )
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Transcribing...",
            style = MaterialTheme.typography.bodyMedium.copy(brush = brush)
        )
    }
}

@Composable
fun VoiceWaveform(amplitude: Int) {
    val bars = 5
    val multipliers = listOf(0.5f, 0.8f, 1.0f, 0.8f, 0.5f)
    
    val normalized = remember(amplitude) {
        val amp = amplitude.coerceIn(10, 25000).toFloat()
        (kotlin.math.log10(amp) - 1f) / (4.398f - 1f)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(bars) { index ->
            val height by animateDpAsState(
                targetValue = (4 + (20 * normalized * multipliers[index])).dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "WaveformBarHeight"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun PulsatingTrashIcon(progress: Float) {
    val pulseDuration = (600 - (progress * 400)).toInt().coerceAtLeast(160)
    
    val infiniteTransition = rememberInfiniteTransition(label = "TrashPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f + (progress * 0.2f),
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TrashScale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TrashAlpha"
    )
    
    Icon(
        Icons.Default.Delete,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error.copy(alpha = alpha),
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    )
}

@Composable
fun MainActionButton(
    state: KeyboardState,
    showLongPressHint: Boolean,
    onSwipeProgress: (Float) -> Unit,
    onSwipeUpProgress: (Float) -> Unit,
    onMicAction: () -> Unit,
    onDiscardAction: () -> Unit,
    onSendAction: () -> Unit,
    onDismissHint: () -> Unit,
    onLockAction: () -> Unit,
    onUnlockAction: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentState by rememberUpdatedState(state)
    val swipeThreshold = 300f
    val swipeUpThreshold = 200f
    
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
        when (state) {
            KeyboardState.Recording -> MaterialTheme.colorScheme.error
            KeyboardState.RecordingLocked -> MaterialTheme.colorScheme.tertiary
            KeyboardState.Paused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        label = "ContainerColor"
    )

    val contentColor by animateColorAsState(
        when (state) {
            KeyboardState.Recording -> MaterialTheme.colorScheme.onError
            KeyboardState.RecordingLocked -> MaterialTheme.colorScheme.onTertiary
            KeyboardState.Paused -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        label = "ContentColor"
    )

    val icon = when (state) {
        KeyboardState.Paused -> Icons.AutoMirrored.Filled.Send
        KeyboardState.RecordingLocked -> Icons.Default.Lock
        else -> Icons.Default.Mic
    }

    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showLongPressHint) {
            Popup(
                alignment = Alignment.BottomCenter,
                offset = androidx.compose.ui.unit.IntOffset(-110, -160),
                properties = PopupProperties(clippingEnabled = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = stringResource(R.string.long_press_hint),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
        
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(containerColor)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        val stateAtDown = currentState
                        
                        if (stateAtDown == KeyboardState.Ready) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMicAction()
                            
                            var currentDragX = 0f
                            var currentDragY = 0f
                            var didHapticAtThreshold = false
                            var didHapticAtLockThreshold = false
                            
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                
                                if (event.type == PointerEventType.Move) {
                                    val dragDelta = change.positionChange()
                                    currentDragX += dragDelta.x
                                    currentDragY += dragDelta.y
                                    
                                    val leftProgress = ((-currentDragX) / swipeThreshold).coerceIn(0f, 1f)
                                    val upProgress = ((-currentDragY) / swipeUpThreshold).coerceIn(0f, 1f)
                                    
                                    if (leftProgress > upProgress) {
                                        onSwipeProgress(leftProgress)
                                        onSwipeUpProgress(0f)
                                        
                                        if (leftProgress >= 1f && !didHapticAtThreshold) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            didHapticAtThreshold = true
                                        } else if (leftProgress < 0.9f) {
                                            didHapticAtThreshold = false
                                        }
                                    } else if (upProgress > 0.05f) {
                                        onSwipeUpProgress(upProgress)
                                        onSwipeProgress(0f)
                                        
                                        if (upProgress >= 1f && !didHapticAtLockThreshold) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            didHapticAtLockThreshold = true
                                        } else if (upProgress < 0.9f) {
                                            didHapticAtLockThreshold = false
                                        }
                                    } else {
                                        onSwipeProgress(0f)
                                        onSwipeUpProgress(0f)
                                    }
                                }
                                
                                if (!change.pressed) {
                                    val finalLeftProgress = ((-currentDragX) / swipeThreshold).coerceIn(0f, 1f)
                                    val finalUpProgress = ((-currentDragY) / swipeUpThreshold).coerceIn(0f, 1f)
                                    
                                    if (finalLeftProgress >= 1f && finalLeftProgress > finalUpProgress) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDiscardAction()
                                    } else if (finalUpProgress >= 1f && finalUpProgress > finalLeftProgress) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onLockAction()
                                    } else {
                                        onMicAction()
                                    }
                                    onSwipeProgress(0f)
                                    onSwipeUpProgress(0f)
                                    break
                                }
                            }
                        } else if (stateAtDown == KeyboardState.RecordingLocked) {
                            var currentDragX = 0f
                            var didHapticAtThreshold = false
                            
                            val up = withTimeoutOrNull(400) {
                                waitForUpOrCancellation()
                            }
                            
                            if (up != null) {
                                onUnlockAction()
                            } else {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    
                                    if (event.type == PointerEventType.Move) {
                                        val dragDelta = change.positionChange()
                                        currentDragX += dragDelta.x
                                        
                                        val leftProgress = ((-currentDragX) / swipeThreshold).coerceIn(0f, 1f)
                                        onSwipeProgress(leftProgress)
                                        
                                        if (leftProgress >= 1f && !didHapticAtThreshold) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            didHapticAtThreshold = true
                                        } else if (leftProgress < 0.9f) {
                                            didHapticAtThreshold = false
                                        }
                                    }
                                    
                                    if (!change.pressed) {
                                        val finalProgress = ((-currentDragX) / swipeThreshold).coerceIn(0f, 1f)
                                        if (finalProgress >= 1f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDiscardAction()
                                        }
                                        onSwipeProgress(0f)
                                        break
                                    }
                                }
                            }
                        } else if (stateAtDown == KeyboardState.Paused) {
                            val up = withTimeoutOrNull(400) {
                                waitForUpOrCancellation()
                            }
                            if (up != null) {
                                onSendAction()
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMicAction()
                                
                                var currentDragX = 0f
                                var currentDragY = 0f
                                var didHapticAtThreshold = false
                                var didHapticAtLockThreshold = false
                                
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    
                                    if (event.type == PointerEventType.Move) {
                                        val dragDelta = change.positionChange()
                                        currentDragX += dragDelta.x
                                        currentDragY += dragDelta.y
                                        
                                        val leftProgress = ((-currentDragX) / swipeThreshold).coerceIn(0f, 1f)
                                        val upProgress = ((-currentDragY) / swipeUpThreshold).coerceIn(0f, 1f)
                                        
                                        if (leftProgress > upProgress) {
                                            onSwipeProgress(leftProgress)
                                            onSwipeUpProgress(0f)
                                            
                                            if (leftProgress >= 1f && !didHapticAtThreshold) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                didHapticAtThreshold = true
                                            } else if (leftProgress < 0.9f) {
                                                didHapticAtThreshold = false
                                            }
                                        } else if (upProgress > 0.05f) {
                                            onSwipeUpProgress(upProgress)
                                            onSwipeProgress(0f)
                                            
                                            if (upProgress >= 1f && !didHapticAtLockThreshold) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                didHapticAtLockThreshold = true
                                            } else if (upProgress < 0.9f) {
                                                didHapticAtLockThreshold = false
                                            }
                                        } else {
                                            onSwipeProgress(0f)
                                            onSwipeUpProgress(0f)
                                        }
                                    }
                                    
                                    if (!change.pressed) {
                                        val finalLeftProgress = ((-currentDragX) / swipeThreshold).coerceIn(0f, 1f)
                                        val finalUpProgress = ((-currentDragY) / swipeUpThreshold).coerceIn(0f, 1f)
                                        
                                        if (finalLeftProgress >= 1f && finalLeftProgress > finalUpProgress) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDiscardAction()
                                        } else if (finalUpProgress >= 1f && finalUpProgress > finalLeftProgress) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onLockAction()
                                        } else {
                                            onMicAction()
                                        }
                                        onSwipeProgress(0f)
                                        onSwipeUpProgress(0f)
                                        break
                                    }
                                }
                            }
                        }
                    }
                },
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
    val whimsicalWords = listOf(
        "Polishing", "Refining", "Synthesizing", "De-umm-ing", "Structuring",
        "Articulating", "Grammarizing", "Decrypting", "Unscrambling", "Enhancing",
        "Harmonizing", "Orchestrating", "Decoding", "Calibrating", "Interpreting",
        "Clarifying", "Perfecting", "Stylizing", "Flowing", "Connecting",
        "Analyzing", "Processing", "Distilling", "Capturing", "Translating",
        "Echoing", "Vocalizing", "Resonating", "Balancing", "Filtering",
        "Reconstructing", "Smoothing", "Fine-tuning", "Adapting", "Reshaping",
        "Aligning", "Synchronizing", "Optimizing", "Gleaning", "Extracting",
        "Transcribing", "Enunciating", "Projecting", "Symphonizing", "Detecting",
        "Listening", "Composing", "Crafting", "Mending", "Rectifying",
        "Mapping", "Weaving", "Sculpting", "Whispering", "Thinking",
        "Untangling", "Crystallizing", "Illuminating", "Channeling", "Parsing",
        "Braiding", "Conjuring", "Manifesting", "Transmuting", "Awakening",
        "Brewing", "Curating", "Deciphering", "Elevating", "Forging",
        "Germinating", "Harvesting", "Infusing", "Kindling", "Layering",
        "Marinating", "Nurturing", "Orchestrating", "Percolating", "Quickening",
        "Radiating", "Simmering", "Tempering", "Unveiling", "Ventilating",
        "Wrangling", "Yielding", "Zeroing", "Assembling", "Blending",
        "Condensing", "Digesting", "Embroidering", "Funneling", "Galvanizing"
    )
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

@Composable
fun ShimmerText(text: String, progress: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "Shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerOffset"
    )
    
    val baseColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
    val highlightColor = MaterialTheme.colorScheme.error
    
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(shimmerOffset * 500f - 100f, 0f),
        end = Offset(shimmerOffset * 500f + 100f, 0f)
    )
    
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
            brush = brush
        ),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.graphicsLayer {
            alpha = 0.7f + (progress * 0.3f)
        }
    )
}

@Composable
fun ShimmerLockText(progress: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "LockShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "LockShimmerOffset"
    )
    
    val baseColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
    val highlightColor = MaterialTheme.colorScheme.tertiary
    
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(shimmerOffset * 500f - 100f, 0f),
        end = Offset(shimmerOffset * 500f + 100f, 0f)
    )
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.swipe_to_lock),
            style = MaterialTheme.typography.titleMedium.copy(brush = brush),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer {
                alpha = 0.7f + (progress * 0.3f)
            }
        )
    }
}
