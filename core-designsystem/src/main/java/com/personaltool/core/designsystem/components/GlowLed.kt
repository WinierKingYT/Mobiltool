package com.personaltool.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personaltool.core.designsystem.theme.IndustrialTheme

enum class LedColor(val coreColor: Color, val glowColor: Color) {
    RED(Color(0xFFFF3B30), Color(0x66FF3B30)),
    AMBER(Color(0xFFFF9500), Color(0x66FF9500)),
    GREEN(Color(0xFF34C759), Color(0x6634C759)),
    CYAN(Color(0xFF30B0C7), Color(0x6630B0C7)),
    COPPER(Color(0xFFBD6B45), Color(0x66BD6B45)),
    OFF(Color(0xFF3A3530), Color.Transparent)
}

@Composable
fun GlowLed(
    color: LedColor,
    isPulsing: Boolean = false,
    size: Dp = 8.dp,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    val alpha = if (isPulsing && color != LedColor.OFF) {
        val infiniteTransition = rememberInfiniteTransition(label = "LedPulse")
        val animatedAlpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "LedPulseAlpha"
        )
        animatedAlpha
    } else {
        1.0f
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(
                    elevation = if (color != LedColor.OFF) (size / 2) else 0.dp,
                    shape = CircleShape,
                    ambientColor = color.glowColor,
                    spotColor = color.glowColor
                )
                .clip(CircleShape)
                .background(color.coreColor.copy(alpha = alpha))
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
        )

        if (label != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = IndustrialTheme.typography.monoSmall,
                color = if (color != LedColor.OFF) IndustrialTheme.colors.textPrimary else IndustrialTheme.colors.textMuted
            )
        }
    }
}
