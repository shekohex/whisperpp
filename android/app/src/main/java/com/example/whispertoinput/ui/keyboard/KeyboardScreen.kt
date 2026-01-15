package com.example.whispertoinput.ui.keyboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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

@Composable
fun KeyboardScreen(
    state: KeyboardState,
    languageLabel: String,
    amplitude: Int,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Surface(
                    onClick = onLanguageClick,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = languageLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Center Status / Mic
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                StatusContent(state, amplitude)
            }

            // Right Cluster
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(visible = state == KeyboardState.Paused) {
                    IconButton(
                        onClick = onSendAction,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, null)
                    }
                }

                AnimatedVisibility(visible = state == KeyboardState.Ready) {
                    BackspaceButton(onDeleteAction)
                }

                AnimatedVisibility(visible = state != KeyboardState.Ready) {
                    IconButton(onClick = onCancelAction) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.width(8.dp))
                
                MicButton(state, onMicAction)
            }
        }
    }
}

@Composable
fun StatusContent(state: KeyboardState, amplitude: Int) {
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
                    stringResource(R.string.input_paused),
                    color = Color(0xFFFFD600), // Material Yellow
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
    
    // Simple log-based normalization
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
fun MicButton(state: KeyboardState, onClick: () -> Unit) {
    val containerColor by animateColorAsState(
        when (state) {
            KeyboardState.Recording -> MaterialTheme.colorScheme.errorContainer
            KeyboardState.Paused -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        label = "MicButtonColor"
    )
    
    val icon = when (state) {
        KeyboardState.Recording -> Icons.Default.Pause
        KeyboardState.Paused -> Icons.Default.PlayArrow
        else -> Icons.Default.Mic
    }

    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor
        )
    ) {
        Icon(icon, null)
    }
}

@Composable
fun BackspaceButton(onDeleteAction: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            onDeleteAction()
            delay(500L) // Initial delay before repeating
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
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    }
                )
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
