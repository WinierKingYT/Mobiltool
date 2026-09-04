package com.personaltool.app.ui.video

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.personaltool.app.video.VideoPlaybackController
import com.personaltool.app.video.VideoPlaybackPhase
import com.personaltool.core.designsystem.components.BadgeSeverity
import com.personaltool.core.designsystem.components.CopperDivider
import com.personaltool.core.designsystem.components.GlowLed
import com.personaltool.core.designsystem.components.InstrumentButton
import com.personaltool.core.designsystem.components.InstrumentButtonStyle
import com.personaltool.core.designsystem.components.LedColor
import com.personaltool.core.designsystem.components.MetricReadout
import com.personaltool.core.designsystem.components.StatusBadge
import com.personaltool.core.designsystem.theme.IndustrialTheme

@Composable
fun VideoPlaybackViewer(
    controller: VideoPlaybackController,
    onDismiss: () -> Unit = { controller.release() },
    modifier: Modifier = Modifier
) {
    val state by controller.state.collectAsState()

    if (state.phase == VideoPlaybackPhase.IDLE) {
        return
    }

    BackHandler {
        onDismiss()
    }

    val colors = IndustrialTheme.colors
    val typography = IndustrialTheme.typography

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Media3 Video Surface
        val player = controller.currentEngine?.playerInstance as? Player
        if (player != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.player = player
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top Industrial HUD Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background.copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                GlowLed(
                    color = when (state.phase) {
                        VideoPlaybackPhase.PLAYING -> LedColor.GREEN
                        VideoPlaybackPhase.READY, VideoPlaybackPhase.PAUSED -> LedColor.AMBER
                        VideoPlaybackPhase.LOADING -> LedColor.CYAN
                        VideoPlaybackPhase.ERROR -> LedColor.RED
                        else -> LedColor.OFF
                    }
                )
                Column {
                    Text(
                        text = state.title.ifBlank { "VIDEO PLAYBACK" },
                        style = typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                    if (state.videoWidth > 0 && state.videoHeight > 0) {
                        Text(
                            text = "${state.videoWidth}x${state.videoHeight}",
                            style = typography.monoSmall,
                            color = colors.accent
                        )
                    }
                }
            }

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Video",
                    tint = colors.textPrimary
                )
            }
        }

        // Bottom Industrial HUD Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface.copy(alpha = 0.90f))
                .align(Alignment.BottomCenter)
        ) {
            CopperDivider()

            // Status Readouts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (badgeText, badgeSeverity) = when (state.phase) {
                    VideoPlaybackPhase.IDLE -> "IDLE" to BadgeSeverity.MUTED
                    VideoPlaybackPhase.LOADING -> "LOADING" to BadgeSeverity.WARNING
                    VideoPlaybackPhase.READY -> "READY" to BadgeSeverity.INFO
                    VideoPlaybackPhase.PLAYING -> "PLAYING" to BadgeSeverity.SUCCESS
                    VideoPlaybackPhase.PAUSED -> "PAUSED" to BadgeSeverity.WARNING
                    VideoPlaybackPhase.COMPLETED -> "COMPLETED" to BadgeSeverity.INFO
                    VideoPlaybackPhase.ERROR -> "ERROR" to BadgeSeverity.DANGER
                }
                StatusBadge(text = badgeText, severity = badgeSeverity)

                MetricReadout(
                    label = "POS / DUR",
                    value = "${formatTime(state.currentPositionMs)} / ${formatTime(state.durationMs)}"
                )
            }

            // Error Message if present
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage ?: "",
                    style = typography.monoSmall,
                    color = colors.danger,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Seek Slider
            if (state.canSeek) {
                Slider(
                    value = state.progressPercent,
                    onValueChange = { percent ->
                        val targetMs = (percent * state.durationMs).toLong()
                        controller.seekTo(targetMs)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.border
                    )
                )
            }

            // Playback Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Seek Rewind
                InstrumentButton(
                    onClick = { controller.seekBy(-10000L) },
                    enabled = state.canSeek,
                    style = InstrumentButtonStyle.SECONDARY
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind 10s",
                        tint = if (state.canSeek) colors.textPrimary else colors.textMuted
                    )
                }

                // Play / Pause Primary Button
                InstrumentButton(
                    onClick = { controller.togglePlayPause() },
                    enabled = state.canPlay || state.canPause,
                    style = InstrumentButtonStyle.PRIMARY,
                    modifier = Modifier.width(120.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (state.phase == VideoPlaybackPhase.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.phase == VideoPlaybackPhase.PLAYING) "Pause" else "Play",
                            tint = colors.textPrimary
                        )
                        Text(
                            text = if (state.phase == VideoPlaybackPhase.PLAYING) "PAUSE" else "PLAY",
                            style = typography.monoMedium,
                            color = colors.textPrimary
                        )
                    }
                }

                // Seek FastForward
                InstrumentButton(
                    onClick = { controller.seekBy(10000L) },
                    enabled = state.canSeek,
                    style = InstrumentButtonStyle.SECONDARY
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward 10s",
                        tint = if (state.canSeek) colors.textPrimary else colors.textMuted
                    )
                }

                // Speed Selectors
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { spd ->
                        val isSelected = kotlin.math.abs(state.playbackSpeed - spd) < 0.05f
                        InstrumentButton(
                            onClick = { controller.setSpeed(spd) },
                            style = if (isSelected) InstrumentButtonStyle.PRIMARY else InstrumentButtonStyle.GHOST
                        ) {
                            Text(
                                text = "${spd}x",
                                style = typography.monoSmall,
                                color = if (isSelected) colors.accent else colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
