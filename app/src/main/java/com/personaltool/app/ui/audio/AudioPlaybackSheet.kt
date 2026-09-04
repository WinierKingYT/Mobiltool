package com.personaltool.app.ui.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.personaltool.app.audio.AudioPlaybackController
import com.personaltool.app.audio.AudioPlaybackPhase
import com.personaltool.core.designsystem.components.BadgeSeverity
import com.personaltool.core.designsystem.components.CopperDivider
import com.personaltool.core.designsystem.components.GlowLed
import com.personaltool.core.designsystem.components.InstrumentButton
import com.personaltool.core.designsystem.components.InstrumentButtonStyle
import com.personaltool.core.designsystem.components.LedColor
import com.personaltool.core.designsystem.components.MetricReadout
import com.personaltool.core.designsystem.components.StatusBadge
import com.personaltool.core.designsystem.components.WaveformVisualizer
import com.personaltool.core.designsystem.theme.IndustrialTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlaybackSheet(
    controller: AudioPlaybackController,
    onDismiss: () -> Unit = { controller.release() },
    modifier: Modifier = Modifier
) {
    val state by controller.state.collectAsState()

    if (state.phase == AudioPlaybackPhase.IDLE) {
        return
    }

    val colors = IndustrialTheme.colors
    val shapes = IndustrialTheme.shapes
    val typography = IndustrialTheme.typography

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(3.dp)
                    .clip(shapes.xs)
                    .background(colors.border)
            )
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(bottom = 24.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    GlowLed(
                        color = when (state.phase) {
                            AudioPlaybackPhase.PLAYING -> LedColor.GREEN
                            AudioPlaybackPhase.LOADING -> LedColor.COPPER
                            AudioPlaybackPhase.ERROR -> LedColor.RED
                            else -> LedColor.COPPER
                        },
                        isPulsing = state.phase == AudioPlaybackPhase.PLAYING,
                        label = "FOREGROUND AUDIO PLAYER"
                    )
                    Text(
                        text = state.title.ifBlank { "Audio Playback" },
                        style = typography.titleLarge,
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Player",
                        tint = colors.textSecondary
                    )
                }
            }

            CopperDivider()

            // Status & Time Readout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (badgeText, badgeSeverity) = when (state.phase) {
                        AudioPlaybackPhase.IDLE -> "IDLE" to BadgeSeverity.MUTED
                        AudioPlaybackPhase.LOADING -> "LOADING" to BadgeSeverity.WARNING
                        AudioPlaybackPhase.READY -> "READY" to BadgeSeverity.INFO
                        AudioPlaybackPhase.PLAYING -> "PLAYING" to BadgeSeverity.SUCCESS
                        AudioPlaybackPhase.PAUSED -> "PAUSED" to BadgeSeverity.WARNING
                        AudioPlaybackPhase.COMPLETED -> "COMPLETED" to BadgeSeverity.INFO
                        AudioPlaybackPhase.ERROR -> "ERROR" to BadgeSeverity.DANGER
                    }

                    StatusBadge(text = badgeText, severity = badgeSeverity)

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricReadout(
                            label = "POSITION",
                            value = formatTime(state.currentPositionMs)
                        )
                        MetricReadout(
                            label = "DURATION",
                            value = formatTime(state.durationMs)
                        )
                    }
                }

                if (state.errorMessage != null && state.phase == AudioPlaybackPhase.ERROR) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.errorMessage ?: "",
                        style = typography.monoSmall,
                        color = colors.danger
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Waveform / Scrubber
                WaveformVisualizer(
                    progressPercent = state.progressPercent,
                    onSeek = { percent ->
                        val targetMs = (percent * state.durationMs).toLong()
                        controller.seekTo(targetMs)
                    },
                    height = 42.dp
                )
            }

            CopperDivider()

            // Transport Control Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed Toggle Button
                InstrumentButton(
                    onClick = {
                        val nextSpeed = when (state.playbackSpeed) {
                            0.5f -> 1.0f
                            1.0f -> 1.5f
                            1.5f -> 2.0f
                            else -> 0.5f
                        }
                        controller.setSpeed(nextSpeed)
                    },
                    style = InstrumentButtonStyle.SECONDARY
                ) {
                    Text(
                        text = "${state.playbackSpeed}x",
                        style = typography.monoSmall,
                        color = colors.accent
                    )
                }

                // Rewind 5s
                InstrumentButton(
                    onClick = { controller.seekBy(-5000L) },
                    style = InstrumentButtonStyle.GHOST,
                    enabled = state.canSeek
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind 5s",
                        tint = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(text = "5S", style = typography.monoSmall, color = colors.textSecondary)
                }

                // Main Play / Pause Button
                InstrumentButton(
                    onClick = { controller.togglePlayPause() },
                    style = InstrumentButtonStyle.PRIMARY,
                    enabled = state.canPlay || state.canPause
                ) {
                    Icon(
                        imageVector = if (state.phase == AudioPlaybackPhase.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.phase == AudioPlaybackPhase.PLAYING) "Pause" else "Play",
                        tint = colors.accent,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = if (state.phase == AudioPlaybackPhase.PLAYING) "PAUSE" else "PLAY",
                        style = typography.monoSmall,
                        color = colors.textPrimary
                    )
                }

                // Forward 5s
                InstrumentButton(
                    onClick = { controller.seekBy(5000L) },
                    style = InstrumentButtonStyle.GHOST,
                    enabled = state.canSeek
                ) {
                    Text(text = "5S", style = typography.monoSmall, color = colors.textSecondary)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward 5s",
                        tint = colors.textSecondary
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}
