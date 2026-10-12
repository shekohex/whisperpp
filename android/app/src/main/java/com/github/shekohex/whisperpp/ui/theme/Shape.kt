package com.github.shekohex.whisperpp.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val WhisperShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

object AppShapes {
    val card = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(8.dp)
    val sheet = RoundedCornerShape(
        topStart = CornerSize(28.dp),
        topEnd = CornerSize(28.dp),
        bottomEnd = CornerSize(0.dp),
        bottomStart = CornerSize(0.dp),
    )
}
