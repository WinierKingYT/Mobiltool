package com.personaltool.app.ui.calls

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personaltool.app.viewmodel.CallsViewModel
import com.personaltool.core.designsystem.components.BadgeSeverity
import com.personaltool.core.designsystem.components.CopperDivider
import com.personaltool.core.designsystem.components.InstrumentButton
import com.personaltool.core.designsystem.components.InstrumentButtonStyle
import com.personaltool.core.designsystem.components.MetricReadout
import com.personaltool.core.designsystem.components.RecordingQualityBadge
import com.personaltool.core.designsystem.components.StatusBadge
import com.personaltool.core.designsystem.components.TechnicalPlate
import com.personaltool.core.designsystem.theme.IndustrialTheme
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.call.RecordingQuality

@Composable
fun CallsScreen(
    viewModel: CallsViewModel,
    onOpenTranscript: (targetId: String, title: String, audioPath: String?, durationMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = IndustrialTheme.colors
    val typography = IndustrialTheme.typography

    val callsList by viewModel.calls.collectAsState()
    val activeState by viewModel.activeCaptureState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Active Capture Control Banner (Zero-cost when idle)
        if (activeState != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.accentStrong.copy(alpha = 0.25f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(text = "RECORDING // ACTIVE", severity = BadgeSeverity.DANGER)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "CALL IN PROGRESS",
                        style = typography.monoSmall,
                        color = colors.textPrimary
                    )
                }

                InstrumentButton(
                    onClick = { viewModel.stopRecording("+90 532 999 8877", "Active Call Partner") },
                    style = InstrumentButtonStyle.DANGER
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Capture",
                        tint = colors.danger,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = "FINALIZE",
                        style = typography.monoSmall,
                        color = colors.textPrimary
                    )
                }
            }
            CopperDivider()
        }

        // Technical Summary Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceSecondary)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricReadout(label = "TOTAL CALLS", value = "${callsList.size}")
            MetricReadout(
                label = "VERIFIED 2-WAY",
                value = "${callsList.count { it.recordingQuality == RecordingQuality.VERIFIED_BIDIRECTIONAL }}",
                isHighlighted = true
            )
            MetricReadout(
                label = "STORAGE USED",
                value = "${callsList.sumOf { it.fileSizeBytes } / 1024 / 1024} MB"
            )

            if (activeState == null) {
                InstrumentButton(
                    onClick = { viewModel.startRecording("+90 532 999 8877", "New Incoming Call") },
                    style = InstrumentButtonStyle.PRIMARY
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Simulate Capture",
                        tint = colors.accent,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = "NEW CALL",
                        style = typography.monoSmall,
                        color = colors.textPrimary
                    )
                }
            }
        }

        CopperDivider()

        // Calls List
        if (callsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO CALLS IN ARCHIVE",
                    style = typography.monoMedium,
                    color = colors.textMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(callsList, key = { it.id }) { call ->
                    TechnicalPlate(
                        categoryTag = "${call.direction.name} // DURATION: ${formatDuration(call.durationMs)}",
                        title = call.contactName ?: call.phoneNumber,
                        subtitle = if (call.contactName != null) call.phoneNumber else null,
                        isActive = call.recordingQuality == RecordingQuality.VERIFIED_BIDIRECTIONAL,
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.toggleFavorite(call) }) {
                                    Icon(
                                        imageVector = if (call.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Favorite",
                                        tint = if (call.isFavorite) colors.accent else colors.textMuted
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteCall(call.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = colors.textMuted
                                    )
                                }
                            }
                        },
                        bottomMetadata = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RecordingQualityBadge(quality = call.recordingQuality)

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    InstrumentButton(
                                        onClick = {
                                            onOpenTranscript(
                                                call.id,
                                                call.contactName ?: call.phoneNumber,
                                                call.audioFilePath,
                                                call.durationMs
                                            )
                                        },
                                        style = InstrumentButtonStyle.PRIMARY
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = colors.accent,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text(
                                            text = "PLAY",
                                            style = typography.monoSmall,
                                            color = colors.textPrimary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    InstrumentButton(
                                        onClick = {
                                            onOpenTranscript(
                                                call.id,
                                                call.contactName ?: call.phoneNumber,
                                                call.audioFilePath,
                                                call.durationMs
                                            )
                                        },
                                        style = InstrumentButtonStyle.SECONDARY,
                                        enabled = call.recordingQuality.isTranscribable
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TextFields,
                                            contentDescription = "Transcribe",
                                            tint = colors.textSecondary,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text(
                                            text = if (call.hasTranscript) "VIEW TXT" else "TRANSCRIBE",
                                            style = typography.monoSmall,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
