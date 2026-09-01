package com.personaltool.app.ui.transcript

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personaltool.app.viewmodel.TranscriptViewModel
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
import com.personaltool.core.model.transcript.TranscriptSegment
import com.personaltool.core.model.transcript.TranscriptStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptViewerSheet(
    viewModel: TranscriptViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = IndustrialTheme.colors
    val shapes = IndustrialTheme.shapes
    val typography = IndustrialTheme.typography

    val state by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    if (state.isOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeTranscript() },
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
                    .fillMaxHeight(0.92f)
                    .background(colors.background)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlowLed(
                                color = if (state.isPlaying) LedColor.COPPER else LedColor.GREEN,
                                isPulsing = state.isPlaying,
                                label = "SEEK-SYNC TRANSCRIPT ENGINE"
                            )
                        }
                        Text(
                            text = state.targetTitle,
                            style = typography.titleLarge,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { viewModel.closeTranscript() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textSecondary
                        )
                    }
                }

                CopperDivider()

                // Playback Control & Waveform Visualizer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InstrumentButton(
                                onClick = { viewModel.togglePlayPause() },
                                style = InstrumentButtonStyle.PRIMARY
                            ) {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = colors.accent,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Text(
                                    text = if (state.isPlaying) "PAUSE" else "PLAY",
                                    style = typography.monoSmall,
                                    color = colors.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            MetricReadout(
                                label = "CURRENT TIME",
                                value = formatTime(state.currentPlaybackPositionMs)
                            )
                        }

                        MetricReadout(
                            label = "TOTAL LENGTH",
                            value = formatTime(state.totalDurationMs)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Real Interactive Waveform Scrubber
                    val progressRatio = if (state.totalDurationMs > 0)
                        state.currentPlaybackPositionMs.toFloat() / state.totalDurationMs.toFloat()
                    else 0f

                    WaveformVisualizer(
                        progressPercent = progressRatio,
                        onSeek = { percent ->
                            val targetMs = (percent * state.totalDurationMs).toLong()
                            viewModel.seekToPosition(targetMs)
                        },
                        height = 38.dp
                    )
                }

                CopperDivider()

                // Search Filter Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceSecondary)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shapes.xs)
                            .background(colors.surface)
                            .border(BorderStroke(1.dp, colors.border), shapes.xs)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colors.textMuted,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            textStyle = typography.monoSmall.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Filter transcript text or timestamps...",
                                        style = typography.monoSmall,
                                        color = colors.textMuted
                                    )
                                }
                                inner()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.height(20.dp).width(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = colors.textMuted
                                )
                            }
                        }
                    }
                }

                CopperDivider()

                // Export Notification Banner
                if (state.exportSuccessMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.success.copy(alpha = 0.2f))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.exportSuccessMessage ?: "",
                            style = typography.monoSmall,
                            color = colors.success
                        )
                    }
                    CopperDivider()
                }

                // Transcript Content or Transcribe Action
                when (state.status) {
                    TranscriptStatus.NONE -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "NO LOCAL TRANSCRIPT FOUND",
                                    style = typography.monoMedium,
                                    color = colors.textMuted
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                InstrumentButton(
                                    onClick = { viewModel.requestTranscription() },
                                    style = InstrumentButtonStyle.PRIMARY
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TextFields,
                                        contentDescription = "Transcribe",
                                        tint = colors.accent,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                    Text(
                                        text = "RUN ON-DEVICE TRANSCRIPTION",
                                        style = typography.monoSmall,
                                        color = colors.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    TranscriptStatus.REQUESTED,
                    TranscriptStatus.QUEUED,
                    TranscriptStatus.RUNNING -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                StatusBadge(text = "ON-DEVICE STT PROCESSING", severity = BadgeSeverity.WARNING)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "${state.progressPercent}%",
                                    style = typography.monoLarge,
                                    color = colors.accent
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Decoding speech segments...",
                                    style = typography.bodyMedium,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }

                    TranscriptStatus.FAILED -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                StatusBadge(text = "TRANSCRIPTION FAILED", severity = BadgeSeverity.DANGER)
                                Spacer(modifier = Modifier.height(12.dp))
                                InstrumentButton(
                                    onClick = { viewModel.requestTranscription() },
                                    style = InstrumentButtonStyle.PRIMARY
                                ) {
                                    Text(
                                        text = "RETRY TRANSCRIPTION",
                                        style = typography.monoSmall,
                                        color = colors.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    TranscriptStatus.READY -> {
                        val allSegments = state.transcript?.segments ?: emptyList()
                        val filteredSegments = if (searchQuery.isBlank()) {
                            allSegments
                        } else {
                            allSegments.filter { it.text.contains(searchQuery, ignoreCase = true) }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                contentPadding = PaddingValues(vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredSegments, key = { it.id }) { segment ->
                                    val isActive = segment.id == state.activeSegmentId
                                    TranscriptSegmentPlate(
                                        segment = segment,
                                        isActive = isActive,
                                        onClick = { viewModel.seekToSegment(segment) }
                                    )
                                }
                            }

                            CopperDivider()

                            // Technical Export Toolstrip
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.surface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                InstrumentButton(
                                    onClick = { viewModel.copyToClipboard(context) },
                                    style = InstrumentButtonStyle.SECONDARY,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Text(text = "TXT", style = typography.monoSmall, color = colors.textSecondary)
                                }

                                InstrumentButton(
                                    onClick = { viewModel.exportAsSrt(context) },
                                    style = InstrumentButtonStyle.SECONDARY,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = "SRT",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Text(text = "SRT", style = typography.monoSmall, color = colors.textSecondary)
                                }

                                InstrumentButton(
                                    onClick = { viewModel.exportAsMarkdown(context) },
                                    style = InstrumentButtonStyle.SECONDARY,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Markdown",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Text(text = "MD", style = typography.monoSmall, color = colors.textSecondary)
                                }
                            }
                        }
                    }

                    TranscriptStatus.CANCELLED -> {}
                }
            }
        }
    }
}

@Composable
fun TranscriptSegmentPlate(
    segment: TranscriptSegment,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = IndustrialTheme.colors
    val shapes = IndustrialTheme.shapes
    val typography = IndustrialTheme.typography

    val borderStroke = if (isActive) {
        BorderStroke(1.dp, colors.accent)
    } else {
        BorderStroke(1.dp, colors.border)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.xs)
            .background(if (isActive) colors.surfaceSecondary else colors.surface)
            .border(borderStroke, shapes.xs)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .background(colors.accent)
                        .align(Alignment.CenterVertically)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "[${formatTime(segment.startTimeMs)}]",
                        style = typography.monoSmall,
                        color = if (isActive) colors.accent else colors.textMuted
                    )

                    if (!segment.speakerTag.isNullOrBlank()) {
                        StatusBadge(
                            text = segment.speakerTag ?: "",
                            severity = if (segment.speakerTag == "YOU") BadgeSeverity.INFO else BadgeSeverity.MUTED
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = segment.text,
                    style = typography.bodyMedium,
                    color = if (isActive) colors.textPrimary else colors.textSecondary
                )
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
