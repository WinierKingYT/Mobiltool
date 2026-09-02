package com.personaltool.desktop.bridge.client

import com.personaltool.core.common.result.AppResult
import com.personaltool.desktop.bridge.desktop.MouseControlMode
import com.personaltool.desktop.bridge.desktop.RemoteDesktopSessionState
import com.personaltool.desktop.bridge.desktop.RemoteInputEvent
import com.personaltool.desktop.bridge.desktop.StreamQualityProfile
import com.personaltool.desktop.bridge.model.AgentAdapterType
import com.personaltool.desktop.bridge.model.AgentSession
import com.personaltool.desktop.bridge.model.NetworkProfile
import com.personaltool.desktop.bridge.model.RegisteredProject
import com.personaltool.desktop.bridge.model.TransportMode
import com.personaltool.desktop.bridge.model.Workstation
import com.personaltool.desktop.bridge.server.BridgeDaemon
import com.personaltool.desktop.bridge.transport.TransportState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

class RemoteDevClient(
    private val bridgeDaemon: BridgeDaemon = BridgeDaemon()
) {

    suspend fun getWorkstation(): AppResult<Workstation> {
        return AppResult.Success(bridgeDaemon.getWorkstationSummary())
    }

    suspend fun getProjects(): AppResult<List<RegisteredProject>> {
        return AppResult.Success(bridgeDaemon.getRegisteredProjects())
    }

    suspend fun getAgentSessions(): AppResult<List<AgentSession>> {
        return AppResult.Success(bridgeDaemon.getAgentSessions())
    }

    fun pollProjectsFlow(intervalMs: Long = 5000L): Flow<List<RegisteredProject>> = flow {
        while (true) {
            emit(bridgeDaemon.getRegisteredProjects())
            delay(intervalMs)
        }
    }

    fun pollAgentSessionsFlow(intervalMs: Long = 3000L): Flow<List<AgentSession>> = flow {
        while (true) {
            emit(bridgeDaemon.getAgentSessions())
            delay(intervalMs)
        }
    }

    suspend fun pairWithCode(pairingCode: String, deviceId: String): AppResult<Boolean> {
        val success = bridgeDaemon.pairClient(pairingCode, deviceId)
        return if (success) {
            AppResult.Success(true)
        } else {
            AppResult.Error("Invalid or expired pairing code")
        }
    }

    suspend fun startTask(
        projectId: String,
        adapterType: AgentAdapterType,
        prompt: String
    ): AppResult<AgentSession> {
        val session = bridgeDaemon.startTask(projectId, adapterType, prompt)
        return if (session != null) {
            AppResult.Success(session)
        } else {
            AppResult.Error("Failed to start task on project: project not found")
        }
    }

    suspend fun sendPrompt(sessionId: String, prompt: String): AppResult<Unit> {
        val success = bridgeDaemon.sendPrompt(sessionId, prompt)
        return if (success) AppResult.Success(Unit) else AppResult.Error("Session not found")
    }

    suspend fun cancelSession(sessionId: String): AppResult<Unit> {
        val success = bridgeDaemon.cancelSession(sessionId)
        return if (success) AppResult.Success(Unit) else AppResult.Error("Session not found")
    }

    suspend fun respondApproval(approvalId: String, isApproved: Boolean): AppResult<Unit> {
        val success = bridgeDaemon.respondApproval(approvalId, isApproved)
        return if (success) AppResult.Success(Unit) else AppResult.Error("Approval request not found or expired")
    }

    // Remote Desktop & Transport Methods (M9 & M10)
    fun startRemoteDesktopSession(): AppResult<RemoteDesktopSessionState> {
        val state = bridgeDaemon.startRemoteDesktop()
        return AppResult.Success(state)
    }

    fun stopRemoteDesktopSession(): AppResult<Unit> {
        bridgeDaemon.stopRemoteDesktop()
        return AppResult.Success(Unit)
    }

    fun getRemoteDesktopSessionFlow(): StateFlow<RemoteDesktopSessionState> =
        bridgeDaemon.getRemoteDesktopSessionFlow()

    fun getTransportStateFlow(): StateFlow<TransportState> =
        bridgeDaemon.getTransportStateFlow()

    fun switchTransportMode(mode: TransportMode) {
        bridgeDaemon.switchTransportMode(mode)
    }

    fun setNetworkProfile(profile: NetworkProfile) {
        bridgeDaemon.setNetworkProfile(profile)
    }

    fun triggerAutomatedFallback(): TransportMode {
        return bridgeDaemon.triggerAutomatedFallback()
    }

    fun selectDisplay(displayId: String) {
        bridgeDaemon.selectDisplay(displayId)
    }

    fun setQualityProfile(quality: StreamQualityProfile) {
        bridgeDaemon.setQualityProfile(quality)
    }

    fun setMouseMode(mode: MouseControlMode) {
        bridgeDaemon.setMouseMode(mode)
    }

    fun sendInputEvent(event: RemoteInputEvent) {
        bridgeDaemon.injectRemoteInput(event)
    }
}
