package com.personaltool.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personaltool.core.designsystem.theme.IndustrialTheme

@Composable
fun CopperDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = IndustrialTheme.colors.border
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}

@Composable
fun ActiveCopperRule(
    modifier: Modifier = Modifier,
    thickness: Dp = 2.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(IndustrialTheme.colors.accent)
    )
}
