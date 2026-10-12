package com.github.shekohex.whisperpp.ui.keyboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R

private const val SHAKE_DP = 6f

/**
 * "‹ Slide to cancel" (B-06): the label follows the finger towards the start edge and fades out while
 * the trash icon grows and turns red; [committed] plays a short shake.
 */
@Composable
fun CancelHint(
    progress: Float,
    committed: Boolean,
    modifier: Modifier = Modifier,
) {
    val towardStart = if (LocalLayoutDirection.current == LayoutDirection.Ltr) -1f else 1f
    val shake = remember { Animatable(0f) }
    LaunchedEffect(committed) {
        if (!committed) return@LaunchedEffect
        shake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = GestureTokens.COMMIT_FEEDBACK_MS
                val step = GestureTokens.COMMIT_FEEDBACK_MS / 4
                SHAKE_DP at step
                -SHAKE_DP at step * 2
                SHAKE_DP / 2 at step * 3
            },
        )
    }
    val trashTint = lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.error,
        progress,
    )

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            tint = trashTint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    val scale = 1f + (GestureTokens.CANCEL_TRASH_MAX_SCALE - 1f) * progress
                    scaleX = scale
                    scaleY = scale
                    translationX = shake.value * density
                },
        )
        Spacer(Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer {
                translationX = towardStart * progress * GestureTokens.CANCEL_HINT_TRAVEL_DP * density
                alpha = 1f - progress
            },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.slide_to_cancel),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
