package com.personaltool.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 38_RETRO_INDUSTRIAL_DESIGN_SYSTEM.md Locked Color Tokens
val Charcoal950 = Color(0xFF0D0C0A)
val Surface900 = Color(0xFF211B17)
val Surface850 = Color(0xFF2A221D)
val Border700 = Color(0xFF4A3A31)

val Ivory100 = Color(0xFFE9E1D6)
val TextSecondary = Color(0xFFB3A79A)
val TextMuted = Color(0xFF7D7268)

val Copper500 = Color(0xFFBD6B45)
val Rust600 = Color(0xFFA55234)
val Rust800 = Color(0xFF7D3D29)

// Semantic State Tokens
val StatusSuccess = Color(0xFF5A8E6A)
val StatusWarning = Color(0xFFD49B42)
val StatusDanger = Color(0xFFB84A39)
val StatusInfo = Color(0xFF6B8A9E)

@Immutable
data class IndustrialColorScheme(
    val background: Color = Charcoal950,
    val surface: Color = Surface900,
    val surfaceSecondary: Color = Surface850,
    val border: Color = Border700,
    val textPrimary: Color = Ivory100,
    val textSecondary: Color = TextSecondary,
    val textMuted: Color = TextMuted,
    val accent: Color = Copper500,
    val accentStrong: Color = Rust600,
    val accentDeep: Color = Rust800,
    val success: Color = StatusSuccess,
    val warning: Color = StatusWarning,
    val danger: Color = StatusDanger,
    val info: Color = StatusInfo
)

val LocalIndustrialColors = staticCompositionLocalOf { IndustrialColorScheme() }
