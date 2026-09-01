package com.personaltool.desktop.bridge.desktop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VirtualScreenCoordinateTransformerTest {

    @Test
    fun transformNormalizedToWindowsAbsolute_mapsCornersAccurately() {
        val display = DisplayOutput(id = "display-1", name = "Primary Monitor", width = 1920, height = 1080, isPrimary = true)

        // Top-left (0.0, 0.0) -> (0, 0)
        val topLeft = VirtualScreenCoordinateTransformer.transformNormalizedToWindowsAbsolute(0f, 0f, display)
        assertThat(topLeft.first).isEqualTo(0)
        assertThat(topLeft.second).isEqualTo(0)

        // Center (0.5, 0.5) -> (32767, 32767)
        val center = VirtualScreenCoordinateTransformer.transformNormalizedToWindowsAbsolute(0.5f, 0.5f, display)
        assertThat(center.first).isIn(32700..32800)
        assertThat(center.second).isIn(32700..32800)

        // Bottom-right (1.0, 1.0) -> (65535, 65535)
        val bottomRight = VirtualScreenCoordinateTransformer.transformNormalizedToWindowsAbsolute(1f, 1f, display)
        assertThat(bottomRight.first).isEqualTo(65535)
        assertThat(bottomRight.second).isEqualTo(65535)
    }

    @Test
    fun transformNormalizedToWindowsAbsolute_clampsOutOfBoundsValues() {
        val display = DisplayOutput(id = "display-1", name = "Primary Monitor", width = 1920, height = 1080, isPrimary = true)

        val outOfBounds = VirtualScreenCoordinateTransformer.transformNormalizedToWindowsAbsolute(-0.5f, 1.5f, display)
        assertThat(outOfBounds.first).isEqualTo(0)
        assertThat(outOfBounds.second).isEqualTo(65535)
    }
}
