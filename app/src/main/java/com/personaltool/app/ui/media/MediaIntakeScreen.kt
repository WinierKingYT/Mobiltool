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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personaltool.app.viewmodel.MediaIntakeViewModel
import com.personaltool.core.designsystem.components.CopperDivider
import com.personaltool.core.designsystem.components.DownloadStatusBadge
import com.personaltool.core.designsystem.components.InstrumentButton
import com.personaltool.core.designsystem.components.InstrumentButtonStyle
import com.personaltool.core.designsystem.components.TechnicalPlate
import com.personaltool.core.designsystem.theme.IndustrialTheme
import com.personaltool.core.model.media.DownloadStatus
import java.io.File

@Composable
fun MediaIntakeScreen(
    viewModel: MediaIntakeViewModel,
    initialUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = IndustrialTheme.colors
    val shapes = IndustrialTheme.shapes
    val typography = IndustrialTheme.typography

    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank() && state.inputUrl.isBlank()) {
            viewModel.onUrlChanged(initialUrl)
            viewModel.probeUrl()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Section Header
        Text(
            text = "URL INTAKE & MEDIA PROBE",
            style = typography.monoSmall,
            color = colors.accent
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Inspect and Archive Media",
            style = typography.titleLarge,
            color = colors.textPrimary
        )
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
                            text = "Paste YouTube, Instagram, X or Media URL...",
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
                text = if (state.isProbing) "PROBING METADATA..." else "INSPECT / PROBE URL",
                style = typography.monoSmall,
                color = colors.textPrimary
            )
        }

        // Probe Results Panel
        state.probeResult?.let { result ->
            Spacer(modifier = Modifier.height(16.dp))
            CopperDivider()
            Spacer(modifier = Modifier.height(16.dp))

            TechnicalPlate(
                categoryTag = "${result.sourcePlatform.name} // DURATION: ${result.durationMs / 60000} MIN",
                title = result.title,
                subtitle = "Uploader: ${result.uploader ?: "Unknown"}",
                isActive = true,
                bottomMetadata = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DownloadStatusBadge(status = state.downloadStatus)
                        Text(
                            text = if (state.downloadProgressPercent > 0) "${state.downloadProgressPercent}%" else "READY TO DOWNLOAD",
                            style = typography.monoSmall,
                            color = if (state.downloadStatus == DownloadStatus.COMPLETED) colors.success else colors.textSecondary
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Format Selection Plate
            Text(
                text = "SELECT TARGET FORMAT",
                style = typography.monoSmall,
                color = colors.textMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            // Download Trigger Button (User Intent Gate)
            InstrumentButton(
                onClick = {
                    val mediaDir = File(context.filesDir, "vault/media")
                    viewModel.startDownload(mediaDir)
                },
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
                    text = if (state.downloadStatus == DownloadStatus.COMPLETED) "STORED IN VAULT" else "DOWNLOAD SELECTED FORMAT",
                    style = typography.monoSmall,
                    color = colors.textPrimary
                )
            }
        }
    }
}
