package com.personaltool.desktop.bridge.desktop

import com.personaltool.desktop.bridge.model.TransportMode

enum class MouseControlMode(val displayName: String) {
    DIRECT_TOUCH("Direct Touch"),
    TRACKPAD("Trackpad Pointer")
}

enum class StreamQualityProfile(val displayName: String, val resolution: String, val fps: Int, val bitrateKbps: Int) {
    QUALITY("High Quality", "1080p", 30, 6000),
    BALANCED("Balanced", "720p", 30, 3500),
    DATA_SAVER("Data Saver / Low Battery", "720p", 20, 1800)
}

data class DisplayOutput(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val dpiScale: Float = 1.0f,
    val isPrimary: Boolean = true
)

data class RemoteDesktopSessionState(
    val isConnected: Boolean = false,
    val workstationId: String = "WS-WIN11-MAIN",
    val activeDisplay: DisplayOutput = DisplayOutput("display-1", "Display 1 (Primary)", 1920, 1080, 1.0f, true),
    val availableDisplays: List<DisplayOutput> = listOf(
        DisplayOutput("display-1", "Display 1 (Primary)", 1920, 1080, 1.0f, true),
        DisplayOutput("display-2", "Display 2 (Secondary)", 2560, 1440, 1.25f, false)
    ),
    val quality: StreamQualityProfile = StreamQualityProfile.BALANCED,
    val mouseMode: MouseControlMode = MouseControlMode.DIRECT_TOUCH,
    val transportMode: TransportMode = TransportMode.DIRECT_LAN,
    val latencyMs: Int = 0,
    val currentFps: Int = 0,
    val isUacPromptActive: Boolean = false,
    val lastInputFeedback: String? = null
)

sealed interface RemoteInputEvent {
    data class Click(val normalizedX: Float, val normalizedY: Float, val isRightClick: Boolean = false) : RemoteInputEvent
    data class Move(val normalizedX: Float, val normalizedY: Float) : RemoteInputEvent
    data class Scroll(val deltaY: Float) : RemoteInputEvent
    data class KeyChord(val keyName: String, val modifiers: List<String> = emptyList()) : RemoteInputEvent
    data class Text(val text: String) : RemoteInputEvent
}
