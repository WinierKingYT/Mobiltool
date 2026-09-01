package com.personaltool.desktop.bridge.agent

import com.personaltool.desktop.bridge.model.AgentAdapterType
import com.personaltool.desktop.bridge.model.AgentSession
import com.personaltool.desktop.bridge.model.AgentSessionStatus
import com.personaltool.desktop.bridge.model.ApprovalRequest
import java.util.UUID

class SessionMonitor {

    private val sessions = mutableListOf<AgentSession>()

    init {
        val elevenProjectId = UUID.nameUUIDFromBytes("Eleven".toByteArray()).toString()
        val elevenApproval = ApprovalRequest(
            id = UUID.randomUUID().toString(),
            sessionId = "session-antigravity-1",
            projectName = "Eleven",
            adapterType = AgentAdapterType.ANTIGRAVITY,
            humanSummary = "Modify native audio buffer sizing in AudioStreamHandler.kt",
            commandPreview = "git diff -U3 AudioStreamHandler.kt",
            affectedPaths = listOf("src/native/AudioStreamHandler.kt")
        )

        sessions.add(
            AgentSession(
                id = UUID.randomUUID().toString(),
                adapterType = AgentAdapterType.CLAUDE_CODE,
                projectId = UUID.nameUUIDFromBytes("PromtGen".toByteArray()).toString(),
                projectName = "PromtGen",
                title = "Auth Token Refresh & Middleware Refactor",
                status = AgentSessionStatus.RUNNING,
                startedAtEpochMs = System.currentTimeMillis() - 523000,
                lastActivityEpochMs = System.currentTimeMillis() - 12000,
                lastEventSummary = "Running integration tests on auth routes...",
                requiresApproval = false,
                pendingApproval = null
            )
        )
        sessions.add(
            AgentSession(
                id = "session-antigravity-1",
                adapterType = AgentAdapterType.ANTIGRAVITY,
                projectId = elevenProjectId,
                projectName = "Eleven",
                title = "PCM Ring Buffer Optimization",
                status = AgentSessionStatus.WAITING_APPROVAL,
                startedAtEpochMs = System.currentTimeMillis() - 1420000,
                lastActivityEpochMs = System.currentTimeMillis() - 45000,
                lastEventSummary = "Requesting approval to modify AudioStreamHandler.kt",
                requiresApproval = true,
                pendingApproval = elevenApproval
            )
        )
        sessions.add(
            AgentSession(
                id = UUID.randomUUID().toString(),
                adapterType = AgentAdapterType.OPEN_CODE,
                projectId = UUID.nameUUIDFromBytes("PersonalMobileTool".toByteArray()).toString(),
                projectName = "PersonalMobileTool",
                title = "Milestone M8 Remote Dev Control Verification",
                status = AgentSessionStatus.IDLE,
                startedAtEpochMs = System.currentTimeMillis() - 3600000,
                lastActivityEpochMs = System.currentTimeMillis() - 900000,
                lastEventSummary = "Task ready for new user prompt",
                requiresApproval = false,
                pendingApproval = null
            )
        )
    }

    fun getAllSessions(): List<AgentSession> = sessions.toList()

    fun getSessionsForProject(projectId: String): List<AgentSession> =
        sessions.filter { it.projectId == projectId }

    fun startSession(
        projectId: String,
        projectName: String,
        adapterType: AgentAdapterType,
        prompt: String
    ): AgentSession {
        val newSession = AgentSession(
            id = UUID.randomUUID().toString(),
            adapterType = adapterType,
            projectId = projectId,
            projectName = projectName,
            title = prompt.take(50),
            status = AgentSessionStatus.RUNNING,
            startedAtEpochMs = System.currentTimeMillis(),
            lastActivityEpochMs = System.currentTimeMillis(),
            lastEventSummary = "Dispatched from mobile: \"$prompt\"",
            requiresApproval = false,
            pendingApproval = null
        )
        sessions.add(0, newSession)
        return newSession
    }

    fun sendPrompt(sessionId: String, prompt: String): Boolean {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index == -1) return false
        val current = sessions[index]
        sessions[index] = current.copy(
            status = AgentSessionStatus.RUNNING,
            lastActivityEpochMs = System.currentTimeMillis(),
            lastEventSummary = "Processing prompt: \"$prompt\""
        )
        return true
    }

    fun cancelSession(sessionId: String): Boolean {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index == -1) return false
        val current = sessions[index]
        sessions[index] = current.copy(
            status = AgentSessionStatus.CANCELLED,
            lastActivityEpochMs = System.currentTimeMillis(),
            lastEventSummary = "Session cancelled by user from mobile device"
        )
        return true
    }

    fun respondApproval(approvalId: String, isApproved: Boolean): Boolean {
        val sessionIndex = sessions.indexOfFirst { it.pendingApproval?.id == approvalId }
        if (sessionIndex == -1) return false
        val current = sessions[sessionIndex]
        sessions[sessionIndex] = current.copy(
            status = if (isApproved) AgentSessionStatus.RUNNING else AgentSessionStatus.IDLE,
            requiresApproval = false,
            pendingApproval = null,
            lastActivityEpochMs = System.currentTimeMillis(),
            lastEventSummary = if (isApproved) "Approval granted from mobile; executing tool..." else "Approval denied by user"
        )
        return true
    }
}
