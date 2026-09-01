package com.personaltool.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.personaltool.app.viewmodel.VaultItem
import com.personaltool.core.designsystem.components.CopperDivider
import com.personaltool.core.designsystem.components.InstrumentButton
import com.personaltool.core.designsystem.components.InstrumentButtonStyle
import com.personaltool.core.designsystem.components.MetricReadout
import com.personaltool.core.designsystem.components.TechnicalPlate
import com.personaltool.core.designsystem.theme.IndustrialTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
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
            MetricReadout(label = "TOTAL CALLS", value = "${state.totalCallCount}")
            MetricReadout(label = "MEDIA ITEMS", value = "${state.totalMediaCount}")
            MetricReadout(label = "TRANSCRIPTS", value = "${state.totalTranscriptsCount}", isHighlighted = true)
            MetricReadout(label = "VAULT SIZE", value = "${state.totalVaultSizeBytes / 1024 / 1024} MB")
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
                            TechnicalPlate(
                                categoryTag = "CALL // ${call.direction.name} // ${call.recordingQuality.name}",
                                title = item.title,
                                subtitle = "Recorded: $formattedDate • ${(call.fileSizeBytes / 1024)} KB${if (call.hasTranscript) " • Transcribed" else ""}",
                                isActive = false,
                                modifier = Modifier.clickable {
                                    onOpenTranscript(call.id, call.contactName ?: call.phoneNumber, call.audioFilePath, call.durationMs)
                                }
                            )
                        }
                        is VaultItem.Media -> {
                            val media = item.item
                            TechnicalPlate(
                                categoryTag = "MEDIA // ${media.sourcePlatform.name} // ${media.mediaType.name}",
                                title = item.title,
                                subtitle = "Downloaded: $formattedDate • ${(media.fileSizeBytes / 1024 / 1024)} MB${if (media.hasTranscript) " • Transcribed" else ""}",
                                isActive = true,
                                modifier = Modifier.clickable {
                                    onOpenTranscript(media.id, media.title, media.localFilePath, media.durationMs)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
