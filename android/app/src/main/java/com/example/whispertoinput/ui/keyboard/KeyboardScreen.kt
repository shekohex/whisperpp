package com.example.whispertoinput.ui.keyboard

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
import com.example.whispertoinput.R
import com.example.whispertoinput.keyboard.KeyboardState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun KeyboardScreen(
    state: KeyboardState,
    languageLabel: String,
    amplitude: Int,
    recordingTime: Long,
    onMicAction: () -> Unit,
    onCancelAction: () -> Unit,
    onSendAction: () -> Unit,
    onDeleteAction: () -> Unit,
    onOpenSettings: () -> Unit,
    onLanguageClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
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
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        fadeIn() + slideInHorizontally() togetherWith fadeOut() + slideOutHorizontally()
                    },
                    label = "LeftClusterTransition"
                ) { targetState ->
                    when (targetState) {
                        KeyboardState.Recording -> {
                            Text(
                                text = formatTime(recordingTime),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
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

            // Center Status
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                StatusContent(state, amplitude, recordingTime)
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
                
                MainActionButton(state, onMicAction, onSendAction)
            }
        }
    }
}

fun formatTime(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}

@Composable
fun StatusContent(state: KeyboardState, amplitude: Int, recordingTime: Long) {
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
            KeyboardState.Paused -> {
                Text(
                    text = formatTime(recordingTime),
                    color = Color(0xFFFFD600), // Material Yellow
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.animateAlphaLoop()
                )
            }
            KeyboardState.Transcribing -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Transcribing...", color = MaterialTheme.colorScheme.primary)
                }
            }
            KeyboardState.SmartFixing -> {
                WhimsicalStatus()
            }
        }
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
fun MainActionButton(
    state: KeyboardState,
    onMicAction: () -> Unit,
    onSendAction: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentState by rememberUpdatedState(state)
    
    val size by animateDpAsState(
        targetValue = if (state == KeyboardState.Recording) 64.dp else 48.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ButtonSize"
    )

    val containerColor by animateColorAsState(
        when (state) {
            KeyboardState.Recording -> MaterialTheme.colorScheme.error
            KeyboardState.Paused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        label = "ContainerColor"
    )

    val contentColor by animateColorAsState(
        when (state) {
            KeyboardState.Recording -> MaterialTheme.colorScheme.onError
            KeyboardState.Paused -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        label = "ContentColor"
    )

    val icon = when (state) {
        KeyboardState.Paused -> Icons.AutoMirrored.Filled.Send
        else -> Icons.Default.Mic
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
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        val stateAtDown = currentState
                        if (stateAtDown == KeyboardState.Ready) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMicAction() // Start
                            waitForUpOrCancellation()
                            onMicAction() // Pause
                        } else if (stateAtDown == KeyboardState.Paused) {
                            val up = withTimeoutOrNull(400) {
                                waitForUpOrCancellation()
                            }
                            if (up != null) {
                                onSendAction()
                            } else {
                                // Long press detected
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMicAction() // Resume
                                waitForUpOrCancellation()
                                onMicAction() // Pause
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
                modifier = Modifier.size(if (state == KeyboardState.Recording) 32.dp else 24.dp)
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
        "Reconstruct", "Smoothing", "Fine-tuning", "Adapting", "Reshaping",
        "Aligning", "Synchronizing", "Optimizing", "Gleaning", "Extracting",
        "Polishing", "Transcribing", "Enunciating", "Projecting", "Symphonizing",
        "Detecting", "Listening", "Composing", "Crafting", "Mending",
        "Rectifying", "Perfecting", "Mapping", "Weaving", "Sculpting",
        "Polishing", "Refining", "Polishing", "Whispering", "Thinking"
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
            delay(100)
            glyphIndex = (glyphIndex + 1) % glyphs.size
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(glyph, color = MaterialTheme.colorScheme.tertiary, fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Text(word, color = MaterialTheme.colorScheme.tertiary)
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
