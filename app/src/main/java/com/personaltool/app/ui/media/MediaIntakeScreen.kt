package com.personaltool.app.ui.media

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.personaltool.app.viewmodel.MediaIntakeViewModel
import com.personaltool.core.designsystem.components.BadgeSeverity
import com.personaltool.core.designsystem.components.CopperDivider
import com.personaltool.core.designsystem.components.DownloadStatusBadge
import com.personaltool.core.designsystem.components.GlowLed
import com.personaltool.core.designsystem.components.InstrumentButton
import com.personaltool.core.designsystem.components.InstrumentButtonStyle
import com.personaltool.core.designsystem.components.LedColor
import com.personaltool.core.designsystem.components.LinearTelemetryBar
import com.personaltool.core.designsystem.components.MetricReadout
import com.personaltool.core.designsystem.components.StatusBadge
import com.personaltool.core.designsystem.components.TechnicalPlate
import com.personaltool.core.designsystem.theme.IndustrialTheme
import com.personaltool.core.model.media.DownloadStatus

@Composable
fun MediaIntakeScreen(
    viewModel: MediaIntakeViewModel,
    initialUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = IndustrialTheme.colors
    val shapes = IndustrialTheme.shapes
    val typography = IndustrialTheme.typography

    val state by viewModel.uiState.collectAsState()
    val libraryItems by viewModel.libraryItems.collectAsState()

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank() && state.inputUrl.isBlank()) {
            viewModel.onUrlChanged(initialUrl)
            viewModel.probeUrl()
        }
    }

    // Fullscreen / Embedded Video Player Viewport
    if (state.activePlayingVideoPath != null) {
        ExoPlayerVideoViewer(
            filePath = state.activePlayingVideoPath!!,
            title = state.activePlayingVideoTitle ?: "Media Stream",
            onClose = { viewModel.closeVideoViewer() },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "HTTP MEDIA ENGINE // EXOPLAYER",
                    style = typography.monoSmall,
                    color = colors.accent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Live Stream Extractor & Vault",
                    style = typography.titleLarge,
                    color = colors.textPrimary
                )
            }

            GlowLed(
                color = if (state.downloadStatus == DownloadStatus.DOWNLOADING) LedColor.AMBER else LedColor.GREEN,
                isPulsing = state.downloadStatus == DownloadStatus.DOWNLOADING,
                label = if (state.downloadStatus == DownloadStatus.DOWNLOADING) "DOWNLOADING" else "ENGINE READY"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // URL Input Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shapes.xs)
                .background(colors.surface)
                .border(BorderStroke(1.dp, colors.border), shapes.xs)
                .padding(12.dp)
        ) {
            BasicTextField(
                value = state.inputUrl,
                onValueChange = { viewModel.onUrlChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = typography.bodyLarge.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                decorationBox = { innerTextField ->
                    if (state.inputUrl.isEmpty()) {
                        Text(
                            text = "Paste direct MP4, M3U8, or platform media link...",
                            style = typography.bodyLarge,
                            color = colors.textMuted
                        )
                    }
                    innerTextField()
                }
            )
        }

        if (state.errorMessage != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.errorMessage ?: "",
                style = typography.monoSmall,
                color = colors.danger
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Probe Button
        InstrumentButton(
            onClick = { viewModel.probeUrl() },
            modifier = Modifier.fillMaxWidth(),
            style = InstrumentButtonStyle.PRIMARY,
            enabled = state.inputUrl.isNotBlank() && !state.isProbing
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Probe",
                tint = colors.accent,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = if (state.isProbing) "PROBING HTTP STREAM HEADERS..." else "INSPECT & PROBE STREAM",
                style = typography.monoSmall,
                color = colors.textPrimary
            )
        }

        // Active Download Telemetry Bar
        if (state.downloadStatus == DownloadStatus.DOWNLOADING) {
            Spacer(modifier = Modifier.height(14.dp))
            LinearTelemetryBar(
                label = "STREAMING CHUNKS // ${state.downloadedBytes / 1024} KB",
                valueText = "${state.downloadProgressPercent}%",
                percent = state.downloadProgressPercent / 100f
            )
        }

        // Probe Results Panel
        state.probeResult?.let { result ->
            Spacer(modifier = Modifier.height(16.dp))
            CopperDivider()
            Spacer(modifier = Modifier.height(16.dp))

            TechnicalPlate(
                categoryTag = "${result.sourcePlatform.name} // SIZE: ${result.fileSizeBytes / 1024 / 1024} MB",
                title = result.title,
                subtitle = "Format MIME: ${result.contentType}",
                isActive = true,
                bottomMetadata = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DownloadStatusBadge(status = state.downloadStatus)
                        Text(
                            text = if (state.downloadProgressPercent > 0) "${state.downloadProgressPercent}%" else "READY TO STREAM",
                            style = typography.monoSmall,
                            color = if (state.downloadStatus == DownloadStatus.COMPLETED) colors.success else colors.textSecondary
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "SELECT STREAM CONTAINER",
                style = typography.monoSmall,
                color = colors.textMuted
            )
            Spacer(modifier = Modifier.height(6.dp))

            result.availableFormats.forEach { format ->
                val isSelected = state.selectedFormatId == format.formatId
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(shapes.xs)
                        .background(if (isSelected) colors.surfaceSecondary else colors.surface)
                        .border(
                            BorderStroke(1.dp, if (isSelected) colors.accent else colors.border),
                            shapes.xs
                        )
                        .clickable { viewModel.selectFormat(format.formatId) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = format.resolution ?: format.formatId,
                                style = typography.bodyMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = format.note ?: format.ext,
                                style = typography.monoSmall,
                                color = colors.textMuted
                            )
                        }

                        Text(
                            text = "${(format.fileSizeBytes ?: 0) / 1024 / 1024} MB",
                            style = typography.monoMedium,
                            color = if (isSelected) colors.accent else colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            InstrumentButton(
                onClick = { viewModel.startDownload() },
                modifier = Modifier.fillMaxWidth(),
                style = InstrumentButtonStyle.PRIMARY,
                enabled = state.selectedFormatId != null && state.downloadStatus != DownloadStatus.DOWNLOADING
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = colors.accent,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = if (state.downloadStatus == DownloadStatus.COMPLETED) "STORED IN VAULT" else "START STREAM DOWNLOAD",
                    style = typography.monoSmall,
                    color = colors.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        CopperDivider()
        Spacer(modifier = Modifier.height(14.dp))

        // Downloaded Media Library Archive Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DOWNLOADED MEDIA ARCHIVE",
                style = typography.monoSmall,
                color = colors.accent
            )
            StatusBadge(text = "${libraryItems.size} ITEMS", severity = BadgeSeverity.MUTED)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (libraryItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO DOWNLOADED MEDIA YET",
                    style = typography.monoSmall,
                    color = colors.textMuted
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                libraryItems.forEach { item ->
                    TechnicalPlate(
                        categoryTag = "${item.sourcePlatform.name} // ${(item.fileSizeBytes) / 1024 / 1024} MB",
                        title = item.title,
                        subtitle = "Path: ${item.localFilePath?.substringAfterLast("/")}",
                        isActive = false,
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteMediaItem(item.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = colors.textMuted
                                )
                            }
                        },
                        bottomMetadata = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusBadge(text = "STORED IN VAULT", severity = BadgeSeverity.SUCCESS)

                                InstrumentButton(
                                    onClick = { viewModel.openVideoViewer(item) },
                                    style = InstrumentButtonStyle.PRIMARY
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = "Play",
                                        tint = colors.accent,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Text(
                                        text = "PLAY MEDIA",
                                        style = typography.monoSmall,
                                        color = colors.textPrimary
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
