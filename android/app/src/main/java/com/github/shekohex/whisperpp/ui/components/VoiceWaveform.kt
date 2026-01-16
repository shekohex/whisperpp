package com.github.shekohex.whisperpp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.abs

@Composable
fun VoiceWaveform(
    modifier: Modifier = Modifier,
    phaseOverride: Float? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val isDark = isSystemInDarkTheme()
    
    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val phase = phaseOverride ?: animatedPhase

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerX = width / 2
        val centerY = height / 2
        
        // Exact SVG ratios (based on 1024 viewBox)
        val r = width / 1024f
        val strokeW = 49f * r
        val spacing = 74f * r
        
        // Final state dimensions (from Lottie/SVG)
        val finalHeights = listOf(288.25f, 102.25f, 175.25f, 102.25f, 288.25f).map { it * r }
        val finalOffsets = listOf(22.375f, 80.875f, 22.625f, 82.375f, 22.375f).map { it * r }
        
        val colors = listOf(
            Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFF4285F4)
        )
        
        for (i in 0 until 5) {
            val x = centerX + (i - 2) * spacing
            
            val barH: Float
            val barY: Float
            
            when {
                // Phase 1: 0-15% Uniform small
                phase < 0.15f -> {
                    barH = 60f * r
                    barY = 0f
                }
                // Phase 2: 15-55% Active Wave
                phase < 0.55f -> {
                    val p = (phase - 0.15f) / 0.40f
                    val staggeredP = (p * 4f - i * 0.25f)
                    val s = sin(staggeredP * PI.toFloat())
                    barH = (100f + 200f * abs(s)) * r
                    barY = -30f * r * s
                }
                // Phase 3: 55-70% Smooth Transition
                phase < 0.70f -> {
                    val p = (phase - 0.55f) / 0.15f
                    val easedP = if (p < 0.5f) 2 * p * p else 1 - (-2 * p + 2).let { it * it } / 2
                    
                    val startH = 150f * r
                    barH = startH + (finalHeights[i] - startH) * easedP
                    barY = 0f + (finalOffsets[i]) * easedP
                }
                // Phase 4: 70-100% Final State
                else -> {
                    barH = finalHeights[i]
                    barY = finalOffsets[i]
                }
            }
            
            drawLine(
                color = colors[i],
                start = Offset(x, centerY + barY - barH / 2),
                end = Offset(x, centerY + barY + barH / 2),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}
