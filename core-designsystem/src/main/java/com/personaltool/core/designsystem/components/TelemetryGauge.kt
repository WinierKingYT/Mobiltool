package com.personaltool.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personaltool.core.designsystem.theme.IndustrialTheme

@Composable
fun CircularTelemetryGauge(
    label: String,
    valueText: String,
    percent: Float,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    gaugeColor: Color = IndustrialTheme.colors.accent,
    trackColor: Color = IndustrialTheme.colors.surfaceSecondary
) {
    val typography = IndustrialTheme.typography
    val colors = IndustrialTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size - 8.dp)) {
                val strokeWidth = 5.dp.toPx()
                val radius = (this.size.minDimension - strokeWidth) / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)

                // 240-degree dial track
                drawArc(
                    color = trackColor,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Active gauge fill
                val activeSweep = (percent.coerceIn(0f, 1f) * 240f)
                drawArc(
                    color = gaugeColor,
                    startAngle = 150f,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = valueText,
                    style = typography.monoMedium,
                    color = colors.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            style = typography.monoSmall,
            color = colors.textMuted
        )
    }
}

@Composable
fun LinearTelemetryBar(
    label: String,
    valueText: String,
    percent: Float,
    modifier: Modifier = Modifier,
    barColor: Color = IndustrialTheme.colors.accent
) {
    val typography = IndustrialTheme.typography
    val colors = IndustrialTheme.colors
    val shapes = IndustrialTheme.shapes

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = typography.monoSmall, color = colors.textMuted)
            Text(text = valueText, style = typography.monoSmall, color = colors.textPrimary)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(colors.surfaceSecondary, shapes.xs)
                .border(0.5.dp, colors.border, shapes.xs)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(barColor, shapes.xs)
            )
        }
    }
}
