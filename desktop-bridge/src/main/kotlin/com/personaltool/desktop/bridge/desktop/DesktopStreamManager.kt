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
                isConnected = false,
                transportMode = transportMode,
                latencyMs = 0,
                currentFps = 0,
                isUacPromptActive = false,
                lastInputFeedback = "Remote desktop stream unlinked (LABS / STANDBY)"
            )
        }
        return _sessionState.value
    }

    fun stopSession() {
        _sessionState.update {
            it.copy(
                isConnected = false,
                latencyMs = 0,
                currentFps = 0,
                isUacPromptActive = false,
                lastInputFeedback = "Session disconnected"
            )
        }
    }

    fun setTransportMode(mode: TransportMode) {
        _sessionState.update {
            it.copy(
                transportMode = mode,
                latencyMs = 0
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
                currentFps = 0
            )
        }
    }

    fun setMouseMode(mode: MouseControlMode) {
        _sessionState.update { it.copy(mouseMode = mode) }
    }

    fun injectInput(event: RemoteInputEvent) {
        val feedback = "Unlinked: Native desktop bridge daemon not connected (input injection unavailable)"
        _sessionState.update { it.copy(lastInputFeedback = feedback) }
    }
}
