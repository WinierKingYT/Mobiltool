package com.personaltool.desktop.bridge.model

enum class TransportMode(val displayName: String, val typicalLatencyMs: Int) {
    DIRECT_LAN("Direct LAN", 12),
    P2P_HOLEPUNCH("P2P WebRTC NAT", 38),
    RELAY_ENCRYPTED("E2EE Relay", 65)
}

enum class NetworkProfile(val displayName: String, val maxBitrateKbps: Int) {
    WIFI_UNMETERED("Wi-Fi (Unmetered)", 6000),
    CELLULAR_METERED("Cellular (Data Saver)", 1800)
}

enum class AgentAdapterType(val displayName: String) {
    CLAUDE_CODE("Claude Code"),
    OPEN_CODE("OpenCode"),
    ANTIGRAVITY("Antigravity"),
    CODEX("Codex / OpenAI")
}

enum class AgentSessionStatus {
    IDLE,
    RUNNING,
    WAITING_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class EndToEndProof(
    val deviceFingerprint: String,
    val workstationFingerprint: String,
    val sessionKeyId: String,
    val algorithm: String = "ChaCha20-Poly1305 / AES-256-GCM",
    val verifiedAtEpochMs: Long = System.currentTimeMillis()
)

data class Workstation(
    val id: String = "WS-UNLINKED",
    val hostname: String = "Unlinked Host",
    val osName: String = "Unknown OS",
    val lanAddress: String = "",
    val bridgeVersion: String = "1.0.0-M10",
    val isOnline: Boolean = false,
    val lastHeartbeatEpochMs: Long = 0L,
    val activeTransport: TransportMode = TransportMode.DIRECT_LAN,
    val networkProfile: NetworkProfile = NetworkProfile.WIFI_UNMETERED,
    val e2eProof: EndToEndProof? = null
)

data class RegisteredProject(
    val id: String,
    val name: String,
    val rootAlias: String,
    val branch: String,
    val isDirty: Boolean,
    val stagedCount: Int,
    val unstagedCount: Int,
    val untrackedCount: Int,
    val aheadCount: Int,
    val behindCount: Int,
    val lastCommitMessage: String,
    val lastCommitHash: String,
    val lastCommitTimestamp: Long,
    val activeAgentSessionsCount: Int
)

data class ApprovalRequest(
    val id: String,
    val sessionId: String,
    val projectName: String,
    val adapterType: AgentAdapterType,
    val humanSummary: String,
    val commandPreview: String? = null,
    val affectedPaths: List<String> = emptyList(),
    val requestedAtEpochMs: Long = System.currentTimeMillis(),
    val expiresAtEpochMs: Long = System.currentTimeMillis() + 600000L // 10 minutes
)

data class AgentSession(
    val id: String,
    val adapterType: AgentAdapterType,
    val projectId: String,
    val projectName: String,
    val title: String,
    val status: AgentSessionStatus,
    val startedAtEpochMs: Long,
    val lastActivityEpochMs: Long,
    val lastEventSummary: String,
    val requiresApproval: Boolean = false,
    val pendingApproval: ApprovalRequest? = null
)

data class PairingToken(
    val pairingCode: String,
    val qrPayload: String,
    val fingerprint: String,
    val workstationId: String,
    val expiresAtEpochMs: Long
)
