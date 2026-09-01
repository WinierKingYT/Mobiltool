package com.personaltool.desktop.bridge.server

import com.personaltool.desktop.bridge.agent.SessionMonitor
import com.personaltool.desktop.bridge.desktop.DesktopStreamManager
import com.personaltool.desktop.bridge.desktop.MouseControlMode
import com.personaltool.desktop.bridge.desktop.RemoteDesktopSessionState
import com.personaltool.desktop.bridge.desktop.RemoteInputEvent
import com.personaltool.desktop.bridge.desktop.StreamQualityProfile
import com.personaltool.desktop.bridge.git.GitInspector
import com.personaltool.desktop.bridge.model.AgentAdapterType
import com.personaltool.desktop.bridge.model.AgentSession
import com.personaltool.desktop.bridge.model.NetworkProfile
import com.personaltool.desktop.bridge.model.PairingToken
import com.personaltool.desktop.bridge.model.RegisteredProject
import com.personaltool.desktop.bridge.model.TransportMode
import com.personaltool.desktop.bridge.model.Workstation
import com.personaltool.desktop.bridge.pairing.PairingManager
import com.personaltool.desktop.bridge.registry.ProjectRegistry
import com.personaltool.desktop.bridge.transport.RemotePresenceManager
import com.personaltool.desktop.bridge.transport.TransportState
import kotlinx.coroutines.flow.StateFlow

class BridgeDaemon(
    val workstation: Workstation = Workstation(
        id = "WS-WIN11-MAIN",
        hostname = "Main-PC",
        lanAddress = "192.168.1.105:8765",
        isOnline = true
    ),
    private val projectRegistry: ProjectRegistry = ProjectRegistry(),
    private val pairingManager: PairingManager = PairingManager(workstation.id),
    private val sessionMonitor: SessionMonitor = SessionMonitor(),
    val desktopStreamManager: DesktopStreamManager = DesktopStreamManager(),
    val presenceManager: RemotePresenceManager = RemotePresenceManager()
) {

    fun getWorkstationSummary(): Workstation {
        val tState = presenceManager.transportState.value
        return workstation.copy(
            activeTransport = tState.currentTransport,
            networkProfile = tState.networkProfile,
            e2eProof = tState.e2eProof,
            lastHeartbeatEpochMs = tState.lastHeartbeatEpochMs
        )
    }

    fun getRegisteredProjects(): List<RegisteredProject> {
        return projectRegistry.getAllProjects().map { entry ->
            val sessionsCount = sessionMonitor.getSessionsForProject(entry.id).size
            GitInspector.inspectProject(entry, sessionsCount)
        }
    }

    fun getAgentSessions(): List<AgentSession> = sessionMonitor.getAllSessions()

    fun createPairingToken(): PairingToken = pairingManager.generatePairingToken()

    fun pairClient(code: String, deviceId: String): Boolean =
        pairingManager.verifyAndPair(code, deviceId)

    fun startTask(
        projectId: String,
        adapterType: AgentAdapterType,
        prompt: String
    ): AgentSession? {
        val project = projectRegistry.getProject(projectId) ?: return null
        return sessionMonitor.startSession(
            projectId = project.id,
            projectName = project.name,
            adapterType = adapterType,
            prompt = prompt
        )
    }

    fun sendPrompt(sessionId: String, prompt: String): Boolean =
        sessionMonitor.sendPrompt(sessionId, prompt)

    fun cancelSession(sessionId: String): Boolean =
        sessionMonitor.cancelSession(sessionId)

    fun respondApproval(approvalId: String, isApproved: Boolean): Boolean =
        sessionMonitor.respondApproval(approvalId, isApproved)

    // Remote Desktop Streaming & Transport (M9 & M10)
    fun startRemoteDesktop(): RemoteDesktopSessionState {
        val transport = presenceManager.transportState.value.currentTransport
        return desktopStreamManager.startSession(transport)
    }

    fun stopRemoteDesktop() = desktopStreamManager.stopSession()

    fun getRemoteDesktopSessionFlow(): StateFlow<RemoteDesktopSessionState> = desktopStreamManager.sessionState

    fun getTransportStateFlow(): StateFlow<TransportState> = presenceManager.transportState

    fun switchTransportMode(mode: TransportMode) {
        presenceManager.switchTransportMode(mode)
        desktopStreamManager.setTransportMode(mode)
    }

    fun setNetworkProfile(profile: NetworkProfile) {
        presenceManager.setNetworkProfile(profile)
        if (profile == NetworkProfile.CELLULAR_METERED) {
            desktopStreamManager.setQualityProfile(StreamQualityProfile.DATA_SAVER)
        }
    }

    fun triggerAutomatedFallback(): TransportMode {
        val newMode = presenceManager.triggerAutomatedFallback()
        desktopStreamManager.setTransportMode(newMode)
        return newMode
    }

    fun selectDisplay(displayId: String) = desktopStreamManager.selectDisplay(displayId)

    fun setQualityProfile(quality: StreamQualityProfile) = desktopStreamManager.setQualityProfile(quality)

    fun setMouseMode(mode: MouseControlMode) = desktopStreamManager.setMouseMode(mode)

    fun injectRemoteInput(event: RemoteInputEvent) = desktopStreamManager.injectInput(event)
}
