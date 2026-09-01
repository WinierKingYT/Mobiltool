package com.personaltool.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personaltool.core.common.result.AppResult
import com.personaltool.desktop.bridge.client.RemoteDevClient
import com.personaltool.desktop.bridge.model.AgentAdapterType
import com.personaltool.desktop.bridge.model.AgentSession
import com.personaltool.desktop.bridge.model.NetworkProfile
import com.personaltool.desktop.bridge.model.RegisteredProject
import com.personaltool.desktop.bridge.model.TransportMode
import com.personaltool.desktop.bridge.model.Workstation
import com.personaltool.desktop.bridge.transport.TransportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteDevUiState(
    val workstation: Workstation? = null,
    val projects: List<RegisteredProject> = emptyList(),
    val agentSessions: List<AgentSession> = emptyList(),
    val transportState: TransportState = TransportState(),
    val isLoading: Boolean = false,
    val isPairingDialogOpen: Boolean = false,
    val pairingInputCode: String = "",
    val isNewTaskDialogOpen: Boolean = false,
    val selectedProjectId: String? = null,
    val selectedAdapter: AgentAdapterType = AgentAdapterType.CLAUDE_CODE,
    val newTaskPrompt: String = "",
    val feedbackMessage: String? = null
)

class RemoteDevViewModel(
    private val remoteDevClient: RemoteDevClient = RemoteDevClient()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteDevUiState())
    val uiState: StateFlow<RemoteDevUiState> = combine(
        _uiState,
        remoteDevClient.getTransportStateFlow()
    ) { base, transport ->
        base.copy(
            transportState = transport,
            workstation = base.workstation?.copy(
                activeTransport = transport.currentTransport,
                networkProfile = transport.networkProfile,
                e2eProof = transport.e2eProof
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RemoteDevUiState()
    )

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, feedbackMessage = null) }

            val wsResult = remoteDevClient.getWorkstation()
            val projResult = remoteDevClient.getProjects()
            val sessResult = remoteDevClient.getAgentSessions()

            val ws = if (wsResult is AppResult.Success) wsResult.data else null
            val projs = if (projResult is AppResult.Success) projResult.data else emptyList()
            val sess = if (sessResult is AppResult.Success) sessResult.data else emptyList()

            _uiState.update {
                it.copy(
                    workstation = ws,
                    projects = projs,
                    agentSessions = sess,
                    selectedProjectId = it.selectedProjectId ?: projs.firstOrNull()?.id,
                    isLoading = false
                )
            }
        }
    }

    // Transport & Network Policy Actions (M10)
    fun switchTransport(mode: TransportMode) {
        remoteDevClient.switchTransportMode(mode)
        _uiState.update { it.copy(feedbackMessage = "SWITCHED TRANSPORT: ${mode.displayName.uppercase()}") }
    }

    fun setNetworkProfile(profile: NetworkProfile) {
        remoteDevClient.setNetworkProfile(profile)
        _uiState.update { it.copy(feedbackMessage = "APPLIED PROFILE: ${profile.displayName.uppercase()}") }
    }

    fun triggerAutomatedFallback() {
        val newMode = remoteDevClient.triggerAutomatedFallback()
        _uiState.update { it.copy(feedbackMessage = "AUTOMATED FALLBACK: ${newMode.displayName.uppercase()}") }
    }

    // Pairing Actions
    fun openPairingDialog() {
        _uiState.update { it.copy(isPairingDialogOpen = true, pairingInputCode = "", feedbackMessage = null) }
    }

    fun closePairingDialog() {
        _uiState.update { it.copy(isPairingDialogOpen = false) }
    }

    fun onPairingCodeChanged(code: String) {
        _uiState.update { it.copy(pairingInputCode = code) }
    }

    fun submitPairingCode() {
        val code = _uiState.value.pairingInputCode
        if (code.isBlank()) return

        viewModelScope.launch {
            when (val result = remoteDevClient.pairWithCode(code, "DEVICE-ANDROID-PIXEL")) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isPairingDialogOpen = false,
                            feedbackMessage = "SUCCESSFULLY PAIRED WITH WORKSTATION"
                        )
                    }
                    refreshAll()
                }
                is AppResult.Error -> {
                    _uiState.update {
                        it.copy(feedbackMessage = "PAIRING FAILED: ${result.message}")
                    }
                }
                AppResult.Loading -> {}
            }
        }
    }

    // Task Creation & Prompting Actions (M8)
    fun openNewTaskDialog(projectId: String? = null) {
        _uiState.update {
            it.copy(
                isNewTaskDialogOpen = true,
                selectedProjectId = projectId ?: it.selectedProjectId ?: it.projects.firstOrNull()?.id,
                newTaskPrompt = "",
                feedbackMessage = null
            )
        }
    }

    fun closeNewTaskDialog() {
        _uiState.update { it.copy(isNewTaskDialogOpen = false) }
    }

    fun onSelectProject(projectId: String) {
        _uiState.update { it.copy(selectedProjectId = projectId) }
    }

    fun onSelectAdapter(adapter: AgentAdapterType) {
        _uiState.update { it.copy(selectedAdapter = adapter) }
    }

    fun onPromptChanged(prompt: String) {
        _uiState.update { it.copy(newTaskPrompt = prompt) }
    }

    fun dispatchNewTask() {
        val state = _uiState.value
        val projectId = state.selectedProjectId ?: return
        val prompt = state.newTaskPrompt
        if (prompt.isBlank()) return

        viewModelScope.launch {
            when (val result = remoteDevClient.startTask(projectId, state.selectedAdapter, prompt)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isNewTaskDialogOpen = false,
                            feedbackMessage = "DISPATCHED TASK TO ${state.selectedAdapter.displayName}"
                        )
                    }
                    refreshAll()
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(feedbackMessage = "FAILED: ${result.message}") }
                }
                AppResult.Loading -> {}
            }
        }
    }

    fun cancelSession(sessionId: String) {
        viewModelScope.launch {
            remoteDevClient.cancelSession(sessionId)
            _uiState.update { it.copy(feedbackMessage = "TASK CANCELLED") }
            refreshAll()
        }
    }

    fun respondToApproval(approvalId: String, isApproved: Boolean) {
        viewModelScope.launch {
            remoteDevClient.respondApproval(approvalId, isApproved)
            val actionName = if (isApproved) "APPROVED" else "REJECTED"
            _uiState.update { it.copy(feedbackMessage = "ACTION $actionName BY USER") }
            refreshAll()
        }
    }
}
