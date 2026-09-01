package com.personaltool.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personaltool.core.designsystem.theme.IndustrialTheme

enum class InstrumentButtonStyle {
    PRIMARY,   // Copper accent outline / filled on active
    SECONDARY, // Neutral surface with hairline border
    DANGER,    // Crimson rust indicator
    GHOST      // Transparent with monospace text
}

@Composable
fun InstrumentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: InstrumentButtonStyle = InstrumentButtonStyle.SECONDARY,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val colors = IndustrialTheme.colors
    val shapes = IndustrialTheme.shapes

    val backgroundColor = when (style) {
        InstrumentButtonStyle.PRIMARY -> if (enabled) colors.accentStrong.copy(alpha = 0.15f) else colors.surfaceSecondary
        InstrumentButtonStyle.SECONDARY -> colors.surface
        InstrumentButtonStyle.DANGER -> colors.danger.copy(alpha = 0.15f)
        InstrumentButtonStyle.GHOST -> Color.Transparent
    }

    val borderColor = when (style) {
        InstrumentButtonStyle.PRIMARY -> if (enabled) colors.accent else colors.border
        InstrumentButtonStyle.SECONDARY -> colors.border
        InstrumentButtonStyle.DANGER -> if (enabled) colors.danger else colors.border
        InstrumentButtonStyle.GHOST -> Color.Transparent
    }

    val contentColor = when (style) {
        InstrumentButtonStyle.PRIMARY -> if (enabled) colors.accent else colors.textMuted
        InstrumentButtonStyle.SECONDARY -> if (enabled) colors.textPrimary else colors.textMuted
        InstrumentButtonStyle.DANGER -> if (enabled) colors.danger else colors.textMuted
        InstrumentButtonStyle.GHOST -> if (enabled) colors.textSecondary else colors.textMuted
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp, minWidth = 64.dp)
            .clip(shapes.xs)
            .background(backgroundColor)
            .border(BorderStroke(1.dp, borderColor), shapes.xs)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
