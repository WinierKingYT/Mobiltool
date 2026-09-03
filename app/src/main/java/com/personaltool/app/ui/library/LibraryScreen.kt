package com.personaltool.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.personaltool.app.viewmodel.LibraryFilter
import com.personaltool.app.viewmodel.LibraryViewModel
import com.personaltool.app.viewmodel.VaultFileState
import com.personaltool.app.viewmodel.VaultItem
import com.personaltool.app.viewmodel.VaultPrimaryAction
import com.personaltool.core.designsystem.components.BadgeSeverity
import com.personaltool.core.designsystem.components.CopperDivider
import com.personaltool.core.designsystem.components.InstrumentButton
import com.personaltool.core.designsystem.components.InstrumentButtonStyle
import com.personaltool.core.designsystem.components.MetricReadout
import com.personaltool.core.designsystem.components.StatusBadge
import com.personaltool.core.designsystem.components.TechnicalPlate
import com.personaltool.core.designsystem.theme.IndustrialTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPlayAudio: (targetId: String, title: String, audioPath: String?, durationMs: Long) -> Unit = { _, _, _, _ -> },
    onPlayVideo: (targetId: String, title: String, filePath: String?) -> Unit = { _, _, _ -> },
    onOpenTranscript: (targetId: String, title: String, audioPath: String?, durationMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = IndustrialTheme.colors
    val shapes = IndustrialTheme.shapes
    val typography = IndustrialTheme.typography

    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Vault Metrics Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceSecondary)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricReadout(label = "INDEXED", value = "${state.indexedItemCount}")
            MetricReadout(label = "AVAILABLE", value = "${state.availableFileCount}")
            MetricReadout(label = "TRANSCRIPTS", value = "${state.totalTranscriptsCount}", isHighlighted = true)
            MetricReadout(label = "VAULT SIZE", value = "${state.availableLocalBytes / 1024 / 1024} MB")
        }

        CopperDivider()

        // Search Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(shapes.xs)
                .background(colors.surface)
                .border(BorderStroke(1.dp, colors.border), shapes.xs)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = typography.bodyMedium.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                decorationBox = { innerTextField ->
                    if (state.searchQuery.isEmpty()) {
                        Text(
                            text = "Search vault archive...",
                            style = typography.bodyMedium,
                            color = colors.textMuted
                        )
                    }
                    innerTextField()
                }
            )
        }

        // Filter Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LibraryFilter.values().forEach { filter ->
                val isSelected = state.filter == filter
                InstrumentButton(
                    onClick = { viewModel.setFilter(filter) },
                    style = if (isSelected) InstrumentButtonStyle.PRIMARY else InstrumentButtonStyle.SECONDARY,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = filter.name,
                        style = typography.monoSmall,
                        color = if (isSelected) colors.textPrimary else colors.textSecondary
                    )
                }
            }
        }

        // Vault Archive List
        if (state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO ITEMS MATCHING FILTER",
                    style = typography.monoMedium,
                    color = colors.textMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    val formattedDate = dateFormat.format(Date(item.createdAt))

                    when (item) {
                        is VaultItem.Call -> {
                            val call = item.session
                            val (badgeText, badgeSeverity) = when (item.fileState) {
                                VaultFileState.AVAILABLE -> "AVAILABLE" to BadgeSeverity.SUCCESS
                                VaultFileState.MISSING -> "MISSING" to BadgeSeverity.DANGER
                                VaultFileState.INVALID_MEDIA -> "INVALID MEDIA" to BadgeSeverity.DANGER
                                VaultFileState.SIZE_MISMATCH -> "SIZE MISMATCH" to BadgeSeverity.WARNING
                                VaultFileState.NO_LOCAL_FILE -> "NO FILE" to BadgeSeverity.MUTED
                                VaultFileState.UNREADABLE -> "UNREADABLE" to BadgeSeverity.DANGER
                                VaultFileState.UNKNOWN -> "UNKNOWN" to BadgeSeverity.MUTED
                            }

                            TechnicalPlate(
                                categoryTag = "CALL // ${call.direction.name} // ${call.recordingQuality.name}",
                                title = item.title,
                                subtitle = "Recorded: $formattedDate • ${(call.fileSizeBytes / 1024)} KB${if (call.hasTranscript) " • Transcribed" else ""}",
                                isActive = item.primaryAction != VaultPrimaryAction.UNAVAILABLE,
                                onClick = {
                                    when (item.primaryAction) {
                                        VaultPrimaryAction.PLAY_AUDIO -> {
                                            onPlayAudio(call.id, item.title, call.audioFilePath, call.durationMs)
                                        }
                                        VaultPrimaryAction.PLAY_VIDEO -> {
                                            onPlayVideo(call.id, item.title, call.audioFilePath)
                                        }
                                        VaultPrimaryAction.OPEN_TRANSCRIPT -> {
                                            onOpenTranscript(call.id, item.title, call.audioFilePath, call.durationMs)
                                        }
                                        VaultPrimaryAction.UNAVAILABLE -> {
                                            // Disabled / No-op
                                        }
                                    }
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StatusBadge(text = badgeText, severity = badgeSeverity)
                                        if (call.hasTranscript && item.primaryAction == VaultPrimaryAction.PLAY_AUDIO) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            InstrumentButton(
                                                onClick = {
                                                    onOpenTranscript(call.id, item.title, call.audioFilePath, call.durationMs)
                                                },
                                                style = InstrumentButtonStyle.GHOST
                                            ) {
                                                Text(
                                                    text = "TXT",
                                                    style = typography.monoSmall,
                                                    color = colors.accent
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        is VaultItem.Media -> {
                            val media = item.item
                            val (badgeText, badgeSeverity) = when (item.fileState) {
                                VaultFileState.AVAILABLE -> "AVAILABLE" to BadgeSeverity.SUCCESS
                                VaultFileState.MISSING -> "MISSING" to BadgeSeverity.DANGER
                                VaultFileState.INVALID_MEDIA -> "INVALID MEDIA" to BadgeSeverity.DANGER
                                VaultFileState.SIZE_MISMATCH -> "SIZE MISMATCH" to BadgeSeverity.WARNING
                                VaultFileState.NO_LOCAL_FILE -> "NO FILE" to BadgeSeverity.MUTED
                                VaultFileState.UNREADABLE -> "UNREADABLE" to BadgeSeverity.DANGER
                                VaultFileState.UNKNOWN -> "UNKNOWN" to BadgeSeverity.MUTED
                            }

                            TechnicalPlate(
                                categoryTag = "MEDIA // ${media.sourcePlatform.name} // ${media.mediaType.name}",
                                title = item.title,
                                subtitle = "Downloaded: $formattedDate • ${(media.fileSizeBytes / 1024 / 1024)} MB${if (media.hasTranscript) " • Transcribed" else ""}",
                                isActive = item.primaryAction != VaultPrimaryAction.UNAVAILABLE,
                                onClick = {
                                    when (item.primaryAction) {
                                        VaultPrimaryAction.PLAY_AUDIO -> {
                                            onPlayAudio(media.id, item.title, media.localFilePath, media.durationMs)
                                        }
                                        VaultPrimaryAction.PLAY_VIDEO -> {
                                            onPlayVideo(media.id, item.title, media.localFilePath)
                                        }
                                        VaultPrimaryAction.OPEN_TRANSCRIPT -> {
                                            onOpenTranscript(media.id, item.title, media.localFilePath, media.durationMs)
                                        }
                                        VaultPrimaryAction.UNAVAILABLE -> {
                                            // Disabled / No-op
                                        }
                                    }
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StatusBadge(text = badgeText, severity = badgeSeverity)
                                        if (media.hasTranscript && (item.primaryAction == VaultPrimaryAction.PLAY_VIDEO || item.primaryAction == VaultPrimaryAction.PLAY_AUDIO)) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            InstrumentButton(
                                                onClick = {
                                                    onOpenTranscript(media.id, item.title, media.localFilePath, media.durationMs)
                                                },
                                                style = InstrumentButtonStyle.GHOST
                                            ) {
                                                Text(
                                                    text = "TXT",
                                                    style = typography.monoSmall,
                                                    color = colors.accent
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
    }
}
