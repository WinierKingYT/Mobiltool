package com.personaltool.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personaltool.core.designsystem.theme.IndustrialTheme

@Composable
fun MetricReadout(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    isHighlighted: Boolean = false
) {
    val colors = IndustrialTheme.colors
    val typography = IndustrialTheme.typography

    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = typography.monoSmall,
            color = colors.textMuted
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (unit != null) "$value $unit" else value,
            style = typography.monoMedium,
            color = if (isHighlighted) colors.accent else colors.textPrimary
        )
    }
}
