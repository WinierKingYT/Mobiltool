package com.personaltool.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personaltool.core.designsystem.theme.IndustrialTheme
import kotlin.random.Random

@Composable
fun WaveformVisualizer(
    progressPercent: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    barCount: Int = 40,
    seed: Long = 42L,
    activeColor: Color = IndustrialTheme.colors.accent,
    inactiveColor: Color = IndustrialTheme.colors.surfaceSecondary,
    cursorColor: Color = IndustrialTheme.colors.textPrimary
) {
    // Generate synthetic realistic waveform peaks based on seed
    val amplitudes = remember(seed, barCount) {
        val rnd = Random(seed)
        List(barCount) {
            val base = 0.25f + rnd.nextFloat() * 0.70f
            // smooth bell curves
            val center = barCount / 2f
            val dist = kotlin.math.abs(it - center) / center
            (base * (1f - dist * 0.35f)).coerceIn(0.15f, 1.0f)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val percent = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(percent)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val percent = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeek(percent)
                }
            }
    ) {
        val totalWidth = size.width
        val canvasHeight = size.height
        val barWidth = (totalWidth / barCount) * 0.65f
        val gap = (totalWidth / barCount) * 0.35f

        val currentProgressX = progressPercent.coerceIn(0f, 1f) * totalWidth

        for (i in 0 until barCount) {
            val barX = i * (barWidth + gap)
            val barHeight = amplitudes[i] * canvasHeight * 0.85f
            val barY = (canvasHeight - barHeight) / 2f

            val isPlayed = barX <= currentProgressX
            val color = if (isPlayed) activeColor else inactiveColor

            drawRoundRect(
                color = color,
                topLeft = Offset(barX, barY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }

        // Playhead Scrubber Cursor Line
        drawRoundRect(
            color = cursorColor,
            topLeft = Offset(currentProgressX - 1.dp.toPx(), 0f),
            size = Size(2.dp.toPx(), canvasHeight),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
        )
    }
}
