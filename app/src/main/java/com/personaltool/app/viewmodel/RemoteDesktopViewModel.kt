package com.personaltool.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personaltool.desktop.bridge.client.RemoteDevClient
import com.personaltool.desktop.bridge.desktop.MouseControlMode
import com.personaltool.desktop.bridge.desktop.RemoteDesktopSessionState
import com.personaltool.desktop.bridge.desktop.RemoteInputEvent
import com.personaltool.desktop.bridge.desktop.StreamQualityProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteDesktopUiState(
    val isViewerOpen: Boolean = false,
    val sessionState: RemoteDesktopSessionState = RemoteDesktopSessionState(),
    val activeModifiers: Set<String> = emptySet(),
    val isKeyboardInputActive: Boolean = false,
    val textInputValue: String = ""
)

class RemoteDesktopViewModel(
    private val remoteDevClient: RemoteDevClient = RemoteDevClient()
) : ViewModel() {

    private val _isViewerOpen = MutableStateFlow(false)
    private val _activeModifiers = MutableStateFlow<Set<String>>(emptySet())
    private val _textInputValue = MutableStateFlow("")

    val uiState: StateFlow<RemoteDesktopUiState> = combine(
        _isViewerOpen,
        remoteDevClient.getRemoteDesktopSessionFlow(),
        _activeModifiers,
        _textInputValue
    ) { isOpen, session, modifiers, textInput ->
        RemoteDesktopUiState(
            isViewerOpen = isOpen,
            sessionState = session,
            activeModifiers = modifiers,
            textInputValue = textInput
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RemoteDesktopUiState()
    )

    fun openViewer() {
        remoteDevClient.startRemoteDesktopSession()
        _isViewerOpen.value = true
    }

    fun closeViewer() {
        remoteDevClient.stopRemoteDesktopSession()
        _activeModifiers.value = emptySet()
        _isViewerOpen.value = false
    }

    fun selectDisplay(displayId: String) {
        remoteDevClient.selectDisplay(displayId)
    }

    fun setQuality(profile: StreamQualityProfile) {
        remoteDevClient.setQualityProfile(profile)
    }

    fun setMouseMode(mode: MouseControlMode) {
        remoteDevClient.setMouseMode(mode)
    }

    fun toggleModifier(modifierName: String) {
        _activeModifiers.update { current ->
            if (current.contains(modifierName)) {
                current - modifierName
            } else {
                current + modifierName
            }
        }
    }

    fun sendClick(normalizedX: Float, normalizedY: Float, isRightClick: Boolean = false) {
        remoteDevClient.sendInputEvent(
            RemoteInputEvent.Click(
                normalizedX = normalizedX.coerceIn(0f, 1f),
                normalizedY = normalizedY.coerceIn(0f, 1f),
                isRightClick = isRightClick
            )
        )
    }

    fun sendKeyChord(keyName: String) {
        val mods = _activeModifiers.value.toList()
        remoteDevClient.sendInputEvent(
            RemoteInputEvent.KeyChord(keyName = keyName, modifiers = mods)
        )
        // Reset active modifier toggle after chord dispatch
        _activeModifiers.value = emptySet()
    }

    fun sendTextInput(text: String) {
        if (text.isNotBlank()) {
            remoteDevClient.sendInputEvent(RemoteInputEvent.Text(text))
            _textInputValue.value = ""
        }
    }

    fun onTextInputChanged(text: String) {
        _textInputValue.value = text
    }
}
