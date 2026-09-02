package com.personaltool.app.ui.remotedev

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.personaltool.app.viewmodel.RemoteDevViewModel
import com.personaltool.core.designsystem.components.BadgeSeverity
import com.personaltool.core.designsystem.components.CopperDivider
import com.personaltool.core.designsystem.components.GlowLed
import com.personaltool.core.designsystem.components.InstrumentButton
import com.personaltool.core.designsystem.components.InstrumentButtonStyle
import com.personaltool.core.designsystem.components.LedColor
import com.personaltool.core.designsystem.components.MetricReadout
import com.personaltool.core.designsystem.components.StatusBadge
import com.personaltool.core.designsystem.components.TechnicalPlate
import com.personaltool.core.designsystem.theme.IndustrialTheme
import com.personaltool.desktop.bridge.model.AgentAdapterType
import com.personaltool.desktop.bridge.model.AgentSessionStatus
import com.personaltool.desktop.bridge.model.NetworkProfile
import com.personaltool.desktop.bridge.model.TransportMode

@Composable
fun RemoteDevScreen(
    viewModel: RemoteDevViewModel,
    onOpenRemoteDesktop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = IndustrialTheme.colors
    val shapes = IndustrialTheme.shapes
    val typography = IndustrialTheme.typography

    val state by viewModel.uiState.collectAsState()
    val transport = state.transportState

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Technical Header Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceSecondary)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val ws = state.workstation
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowLed(
                    color = if (ws?.isOnline == true) LedColor.GREEN else LedColor.RED,
                    isPulsing = ws?.isOnline == true,
                    label = "${ws?.hostname ?: "Desktop Workstation"} // ${ws?.lanAddress ?: "192.168.1.100:8765"}"
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.refreshAll() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = colors.textSecondary
                    )
                }

                InstrumentButton(
                    onClick = onOpenRemoteDesktop,
                    style = InstrumentButtonStyle.PRIMARY
                ) {
                    Icon(
                        imageVector = Icons.Default.DesktopWindows,
                        contentDescription = "Desktop",
                        tint = colors.accent,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(text = "DESKTOP", style = typography.monoSmall, color = colors.textPrimary)
                }

                Spacer(modifier = Modifier.width(4.dp))

                InstrumentButton(
                    onClick = { viewModel.openNewTaskDialog() },
                    style = InstrumentButtonStyle.SECONDARY
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Task",
                        tint = colors.textSecondary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(text = "TASK", style = typography.monoSmall, color = colors.textSecondary)
                }
            }
        }

        CopperDivider()

        // Transport & Network Profile Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val transportSeverity = when (transport.currentTransport) {
                    TransportMode.DIRECT_LAN -> BadgeSeverity.SUCCESS
                    TransportMode.P2P_HOLEPUNCH -> BadgeSeverity.INFO
                    TransportMode.RELAY_ENCRYPTED -> BadgeSeverity.WARNING
                }
                val isConnected = transport.isReachable && transport.rttLatencyMs > 0
                StatusBadge(
                    text = if (isConnected)
                        "${transport.currentTransport.displayName.uppercase()} (${transport.rttLatencyMs}ms)"
                    else
                        "${transport.currentTransport.displayName.uppercase()} (STANDBY)",
                    severity = if (isConnected) transportSeverity else BadgeSeverity.MUTED
                )

                Spacer(modifier = Modifier.width(6.dp))

                InstrumentButton(
                    onClick = { viewModel.triggerAutomatedFallback() },
                    style = InstrumentButtonStyle.GHOST
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = "Fallback",
                        tint = colors.textSecondary,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                    Text(text = "ROUTE", style = typography.monoSmall, color = colors.textSecondary)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val isCellular = transport.networkProfile == NetworkProfile.CELLULAR_METERED
                InstrumentButton(
                    onClick = {
                        val nextProfile = if (isCellular) NetworkProfile.WIFI_UNMETERED else NetworkProfile.CELLULAR_METERED
                        viewModel.setNetworkProfile(nextProfile)
                    },
                    style = if (isCellular) InstrumentButtonStyle.SECONDARY else InstrumentButtonStyle.GHOST
                ) {
                    Icon(
                        imageVector = if (isCellular) Icons.Default.CellTower else Icons.Default.Wifi,
                        contentDescription = "Network Profile",
                        tint = if (isCellular) colors.warning else colors.accent,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = if (isCellular) "CELLULAR" else "WI-FI",
                        style = typography.monoSmall,
                        color = if (isCellular) colors.warning else colors.textPrimary
                    )
                }
            }
        }

        CopperDivider()

        // Notification Banner
        if (state.feedbackMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.success.copy(alpha = 0.2f))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.feedbackMessage ?: "",
                    style = typography.monoSmall,
                    color = colors.success
                )
            }
            CopperDivider()
        }

        // Projects & Sessions List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // E2EE Identity Status
            item {
                val proof = transport.e2eProof
                TechnicalPlate(
                    categoryTag = "SECURITY // END-TO-END CRYPTOGRAPHIC IDENTITY",
                    title = "E2EE ENCRYPTED TUNNEL",
                    subtitle = "Session Key: ${proof.sessionKeyId} • Cipher: ${proof.algorithm}",
                    isActive = false,
                    trailingContent = {
                        StatusBadge(text = "E2EE ACTIVE", severity = BadgeSeverity.SUCCESS)
                    },
                    bottomMetadata = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MetricReadout(label = "PHONE FP", value = proof.deviceFingerprint)
                            MetricReadout(label = "WORKSTATION FP", value = proof.workstationFingerprint)
                            MetricReadout(label = "CIPHER", value = "AES-256-GCM")
                        }
                    }
                )
            }

            // Section 1: Registered Projects
            item {
                Text(
                    text = "01 // REGISTERED CODEBASE PROJECTS",
                    style = typography.monoSmall,
                    color = colors.accent,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            items(state.projects, key = { it.id }) { project ->
                TechnicalPlate(
                    categoryTag = "${project.rootAlias} // BRANCH: ${project.branch}",
                    title = project.name,
                    subtitle = "${project.lastCommitHash} • ${project.lastCommitMessage}",
                    isActive = project.activeAgentSessionsCount > 0,
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(
                                text = if (project.isDirty) "${project.stagedCount + project.unstagedCount} MODIFIED" else "CLEAN",
                                severity = if (project.isDirty) BadgeSeverity.WARNING else BadgeSeverity.SUCCESS
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            InstrumentButton(
                                onClick = { viewModel.openNewTaskDialog(project.id) },
                                style = InstrumentButtonStyle.PRIMARY
                            ) {
                                Text(text = "+ TASK", style = typography.monoSmall, color = colors.textPrimary)
                            }
                        }
                    },
                    bottomMetadata = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MetricReadout(label = "STAGED", value = "${project.stagedCount}")
                            MetricReadout(label = "UNSTAGED", value = "${project.unstagedCount}")
                            MetricReadout(label = "AHEAD", value = "${project.aheadCount}", isHighlighted = project.aheadCount > 0)
                            MetricReadout(label = "ACTIVE AGENTS", value = "${project.activeAgentSessionsCount}", isHighlighted = project.activeAgentSessionsCount > 0)
                        }
                    }
                )
            }

            // Section 2: Agent Sessions Monitor & Control
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "02 // LIVE AGENT SESSIONS & CONSOLE STREAM",
                    style = typography.monoSmall,
                    color = colors.accent,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            items(state.agentSessions, key = { it.id }) { session ->
                val severity = when (session.status) {
                    AgentSessionStatus.RUNNING -> BadgeSeverity.INFO
                    AgentSessionStatus.WAITING_APPROVAL -> BadgeSeverity.WARNING
                    AgentSessionStatus.COMPLETED -> BadgeSeverity.SUCCESS
                    AgentSessionStatus.FAILED -> BadgeSeverity.DANGER
                    AgentSessionStatus.IDLE,
                    AgentSessionStatus.CANCELLED -> BadgeSeverity.MUTED
                }

                TechnicalPlate(
                    categoryTag = "${session.adapterType.displayName.uppercase()} // ${session.projectName}",
                    title = session.title,
                    subtitle = session.lastEventSummary,
                    isActive = session.status == AgentSessionStatus.RUNNING || session.status == AgentSessionStatus.WAITING_APPROVAL,
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(
                                text = session.status.name,
                                severity = severity
                            )

                            if (session.status == AgentSessionStatus.RUNNING) {
                                Spacer(modifier = Modifier.width(6.dp))
                                InstrumentButton(
                                    onClick = { viewModel.cancelSession(session.id) },
                                    style = InstrumentButtonStyle.DANGER
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Cancel",
                                        tint = colors.danger,
                                        modifier = Modifier.padding(end = 2.dp)
                                    )
                                    Text(text = "STOP", style = typography.monoSmall, color = colors.textPrimary)
                                }
                            }
                        }
                    },
                    bottomMetadata = {
                        if (session.requiresApproval && session.pendingApproval != null) {
                            val approval = session.pendingApproval!!
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.surfaceSecondary)
                                    .border(BorderStroke(1.dp, colors.warning), shapes.xs)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "ACTION APPROVAL REQUIRED",
                                    style = typography.monoSmall,
                                    color = colors.warning
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = approval.humanSummary,
                                    style = typography.bodyMedium,
                                    color = colors.textPrimary
                                )
                                if (approval.commandPreview != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$ ${approval.commandPreview}",
                                        style = typography.monoSmall,
                                        color = colors.textSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    InstrumentButton(
                                        onClick = { viewModel.respondToApproval(approval.id, isApproved = true) },
                                        style = InstrumentButtonStyle.PRIMARY,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Approve",
                                            tint = colors.success,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text(text = "APPROVE", style = typography.monoSmall, color = colors.textPrimary)
                                    }

                                    InstrumentButton(
                                        onClick = { viewModel.respondToApproval(approval.id, isApproved = false) },
                                        style = InstrumentButtonStyle.DANGER,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Reject",
                                            tint = colors.danger,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text(text = "REJECT", style = typography.monoSmall, color = colors.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    // New Task Creation Modal Dialog
    if (state.isNewTaskDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.closeNewTaskDialog() },
            containerColor = colors.surface,
            title = {
                Text(
                    text = "DISPATCH NEW AGENT TASK",
                    style = typography.monoMedium,
                    color = colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "1. SELECT TARGET PROJECT:",
                        style = typography.monoSmall,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.projects.forEach { proj ->
                            val isSelected = state.selectedProjectId == proj.id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(shapes.xs)
                                    .background(if (isSelected) colors.surfaceSecondary else colors.background)
                                    .border(BorderStroke(1.dp, if (isSelected) colors.accent else colors.border), shapes.xs)
                                    .clickable { viewModel.onSelectProject(proj.id) }
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = proj.name,
                                    style = typography.monoSmall,
                                    color = if (isSelected) colors.accent else colors.textSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "2. SELECT TOOL ADAPTER:",
                        style = typography.monoSmall,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AgentAdapterType.values().forEach { adapter ->
                            val isSelected = state.selectedAdapter == adapter
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(shapes.xs)
                                    .background(if (isSelected) colors.surfaceSecondary else colors.background)
                                    .border(BorderStroke(1.dp, if (isSelected) colors.accent else colors.border), shapes.xs)
                                    .clickable { viewModel.onSelectAdapter(adapter) }
                                    .padding(vertical = 6.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = adapter.displayName.split(" ").first(),
                                    style = typography.monoSmall,
                                    color = if (isSelected) colors.accent else colors.textSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "3. TASK PROMPT / INSTRUCTIONS:",
                        style = typography.monoSmall,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(shapes.xs)
                            .background(colors.surfaceSecondary)
                            .border(BorderStroke(1.dp, colors.border), shapes.xs)
                            .padding(10.dp)
                    ) {
                        BasicTextField(
                            value = state.newTaskPrompt,
                            onValueChange = { viewModel.onPromptChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = typography.bodyMedium.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            decorationBox = { innerTextField ->
                                if (state.newTaskPrompt.isEmpty()) {
                                    Text(
                                        text = "Describe changes, refactoring or bug fix to execute...",
                                        style = typography.bodyMedium,
                                        color = colors.textMuted
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                InstrumentButton(
                    onClick = { viewModel.dispatchNewTask() },
                    style = InstrumentButtonStyle.PRIMARY,
                    enabled = state.newTaskPrompt.isNotBlank() && state.selectedProjectId != null
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Dispatch",
                        tint = colors.accent,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(text = "DISPATCH TASK", style = typography.monoSmall, color = colors.textPrimary)
                }
            },
            dismissButton = {
                InstrumentButton(
                    onClick = { viewModel.closeNewTaskDialog() },
                    style = InstrumentButtonStyle.GHOST
                ) {
                    Text(text = "CANCEL", style = typography.monoSmall, color = colors.textSecondary)
                }
            }
        )
    }

    // Pairing Modal Dialog
    if (state.isPairingDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.closePairingDialog() },
            containerColor = colors.surface,
            title = {
                Text(
                    text = "PAIR DESKTOP BRIDGE",
                    style = typography.monoMedium,
                    color = colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter the 6-digit one-time pairing code displayed on your Desktop Bridge CLI/daemon:",
                        style = typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shapes.xs)
                            .background(colors.surfaceSecondary)
                            .border(BorderStroke(1.dp, colors.border), shapes.xs)
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = state.pairingInputCode,
                            onValueChange = { viewModel.onPairingCodeChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = typography.monoLarge.copy(color = colors.accent),
                            cursorBrush = SolidColor(colors.accent),
                            decorationBox = { innerTextField ->
                                if (state.pairingInputCode.isEmpty()) {
                                    Text(
                                        text = "e.g. 482910",
                                        style = typography.monoLarge,
                                        color = colors.textMuted
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                InstrumentButton(
                    onClick = { viewModel.submitPairingCode() },
                    style = InstrumentButtonStyle.PRIMARY,
                    enabled = state.pairingInputCode.isNotBlank()
                ) {
                    Text(text = "CONFIRM PAIRING", style = typography.monoSmall, color = colors.textPrimary)
                }
            },
            dismissButton = {
                InstrumentButton(
                    onClick = { viewModel.closePairingDialog() },
                    style = InstrumentButtonStyle.GHOST
                ) {
                    Text(text = "CANCEL", style = typography.monoSmall, color = colors.textSecondary)
                }
            }
        )
    }
}
