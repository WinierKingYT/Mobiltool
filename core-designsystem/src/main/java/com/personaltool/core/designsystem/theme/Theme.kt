package com.personaltool.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val MaterialDarkColors = darkColorScheme(
    primary = Copper500,
    onPrimary = Charcoal950,
    primaryContainer = Rust800,
    onPrimaryContainer = Ivory100,
    secondary = Surface850,
    onSecondary = Ivory100,
    background = Charcoal950,
    onBackground = Ivory100,
    surface = Surface900,
    onSurface = Ivory100,
    surfaceVariant = Surface850,
    onSurfaceVariant = TextSecondary,
    outline = Border700,
    error = StatusDanger,
    onError = Ivory100
)

@Composable
fun IndustrialTheme(
    content: @Composable () -> Unit
) {
    val industrialColors = IndustrialColorScheme()
    val industrialShapes = IndustrialShapes()
    val industrialTypography = IndustrialTypography()

    CompositionLocalProvider(
        LocalIndustrialColors provides industrialColors,
        LocalIndustrialShapes provides industrialShapes,
        LocalIndustrialTypography provides industrialTypography
    ) {
        MaterialTheme(
            colorScheme = MaterialDarkColors,
            content = content
        )
    }
}

object IndustrialTheme {
    val colors: IndustrialColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalIndustrialColors.current

    val shapes: IndustrialShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalIndustrialShapes.current

    val typography: IndustrialTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalIndustrialTypography.current
}
