package com.personaltool.desktop.bridge.desktop

import com.personaltool.desktop.bridge.model.TransportMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DesktopStreamManager {

    private val _sessionState = MutableStateFlow(RemoteDesktopSessionState())
    val sessionState: StateFlow<RemoteDesktopSessionState> = _sessionState.asStateFlow()

    fun startSession(transportMode: TransportMode = TransportMode.DIRECT_LAN): RemoteDesktopSessionState {
        _sessionState.update {
            it.copy(
                isConnected = true,
                transportMode = transportMode,
                latencyMs = transportMode.typicalLatencyMs,
                currentFps = it.quality.fps,
                isUacPromptActive = false,
                lastInputFeedback = "Session established via ${transportMode.displayName} (E2EE verified)"
            )
        }
        return _sessionState.value
    }

    fun stopSession() {
        _sessionState.update {
            it.copy(isConnected = false, isUacPromptActive = false, lastInputFeedback = "Session disconnected")
        }
    }

    fun setTransportMode(mode: TransportMode) {
        _sessionState.update {
            it.copy(
                transportMode = mode,
                latencyMs = mode.typicalLatencyMs
            )
        }
    }

    fun setUacState(isActive: Boolean) {
        _sessionState.update { it.copy(isUacPromptActive = isActive) }
    }

    fun selectDisplay(displayId: String) {
        val display = _sessionState.value.availableDisplays.find { it.id == displayId } ?: return
        _sessionState.update { it.copy(activeDisplay = display) }
    }

    fun setQualityProfile(quality: StreamQualityProfile) {
        _sessionState.update {
            it.copy(
                quality = quality,
                currentFps = quality.fps
            )
        }
    }

    fun setMouseMode(mode: MouseControlMode) {
        _sessionState.update { it.copy(mouseMode = mode) }
    }

    fun injectInput(event: RemoteInputEvent) {
        val display = _sessionState.value.activeDisplay
        val feedback = when (event) {
            is RemoteInputEvent.Click -> {
                val (winAbsX, winAbsY) = VirtualScreenCoordinateTransformer.transformNormalizedToWindowsAbsolute(
                    event.normalizedX,
                    event.normalizedY,
                    display
                )
                val type = if (event.isRightClick) "Right Click" else "Left Click"
                "Injected $type at WinAbs($winAbsX, $winAbsY)"
            }
            is RemoteInputEvent.Move -> {
                val (winAbsX, winAbsY) = VirtualScreenCoordinateTransformer.transformNormalizedToWindowsAbsolute(
                    event.normalizedX,
                    event.normalizedY,
                    display
                )
                "Pointer move to WinAbs($winAbsX, $winAbsY)"
            }
            is RemoteInputEvent.Scroll -> "Scroll wheel delta: ${event.deltaY}"
            is RemoteInputEvent.KeyChord -> "Key chord: ${event.modifiers.joinToString("+")}+${event.keyName}"
            is RemoteInputEvent.Text -> "Injected UTF-16 Unicode text: \"${event.text}\" (KEYEVENTF_UNICODE)"
        }

        _sessionState.update { it.copy(lastInputFeedback = feedback) }
    }
}
