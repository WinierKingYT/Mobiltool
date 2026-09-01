package com.personaltool.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personaltool.core.designsystem.theme.IndustrialTheme

@Composable
fun TechnicalPlate(
    modifier: Modifier = Modifier,
    categoryTag: String? = null,
    title: String,
    subtitle: String? = null,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    bottomMetadata: (@Composable () -> Unit)? = null
) {
    val colors = IndustrialTheme.colors
    val shapes = IndustrialTheme.shapes
    val typography = IndustrialTheme.typography

    val borderStroke = if (isActive) {
        BorderStroke(1.dp, colors.accent)
    } else {
        BorderStroke(1.dp, colors.border)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.xs)
            .background(colors.surface)
            .border(borderStroke, shapes.xs)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Active Signal Indicator (Copper vertical strip on the left edge)
            if (isActive) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .background(colors.accent)
                        .align(Alignment.CenterVertically)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                // Category / Technical Index Tag
                if (!categoryTag.isNullOrBlank()) {
                    Text(
                        text = categoryTag.uppercase(),
                        style = typography.monoSmall,
                        color = if (isActive) colors.accent else colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = typography.titleMedium,
                            color = colors.textPrimary
                        )

                        if (!subtitle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = typography.bodyMedium,
                                color = colors.textSecondary
                            )
                        }
                    }

                    if (trailingContent != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        trailingContent()
                    }
                }

                // Bottom Metadata / Technical readout footer
                if (bottomMetadata != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        bottomMetadata()
                    }
                }
            }
        }
    }
}
