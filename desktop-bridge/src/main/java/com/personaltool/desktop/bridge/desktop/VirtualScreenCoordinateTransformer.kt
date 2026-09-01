package com.personaltool.desktop.bridge.desktop

data class VirtualScreenBounds(
    val left: Int = 0,
    val top: Int = 0,
    val width: Int = 1920,
    val height: Int = 1080
)

object VirtualScreenCoordinateTransformer {

    fun transformNormalizedToWindowsAbsolute(
        normalizedX: Float,
        normalizedY: Float,
        activeDisplay: DisplayOutput,
        virtualScreenBounds: VirtualScreenBounds = VirtualScreenBounds(0, 0, activeDisplay.width, activeDisplay.height)
    ): Pair<Int, Int> {
        val clampX = normalizedX.coerceIn(0f, 1f)
        val clampY = normalizedY.coerceIn(0f, 1f)

        // Pixel coordinate on active physical monitor
        val physicalX = (clampX * activeDisplay.width).toInt()
        val physicalY = (clampY * activeDisplay.height).toInt()

        // Windows MOUSEEVENTF_ABSOLUTE 0..65535 coordinate system mapping across multi-DPI virtual screen
        val winAbsoluteX = ((physicalX - virtualScreenBounds.left) * 65535.0 / virtualScreenBounds.width).toInt().coerceIn(0, 65535)
        val winAbsoluteY = ((physicalY - virtualScreenBounds.top) * 65535.0 / virtualScreenBounds.height).toInt().coerceIn(0, 65535)

        return Pair(winAbsoluteX, winAbsoluteY)
    }
}
