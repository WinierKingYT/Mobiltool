package com.personaltool.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personaltool.core.designsystem.theme.IndustrialTheme
import com.personaltool.core.model.call.RecordingQuality
import com.personaltool.core.model.media.DownloadStatus

enum class BadgeSeverity {
    SUCCESS,
    WARNING,
    DANGER,
    INFO,
    MUTED
}

@Composable
fun StatusBadge(
    text: String,
    severity: BadgeSeverity,
    modifier: Modifier = Modifier
) {
    val colors = IndustrialTheme.colors
    val shapes = IndustrialTheme.shapes
    val typography = IndustrialTheme.typography

    val (badgeColor, dotColor) = when (severity) {
        BadgeSeverity.SUCCESS -> colors.success.copy(alpha = 0.15f) to colors.success
        BadgeSeverity.WARNING -> colors.warning.copy(alpha = 0.15f) to colors.warning
        BadgeSeverity.DANGER -> colors.danger.copy(alpha = 0.15f) to colors.danger
        BadgeSeverity.INFO -> colors.info.copy(alpha = 0.15f) to colors.info
        BadgeSeverity.MUTED -> colors.surfaceSecondary to colors.textMuted
    }

    Box(
        modifier = modifier
            .clip(shapes.xs)
            .background(badgeColor)
            .border(BorderStroke(1.dp, dotColor.copy(alpha = 0.6f)), shapes.xs)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Signal dot
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(shapes.none)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text.uppercase(),
                style = typography.monoSmall,
                color = colors.textPrimary
            )
        }
    }
}

@Composable
fun RecordingQualityBadge(quality: RecordingQuality, modifier: Modifier = Modifier) {
    val (label, severity) = when (quality) {
        RecordingQuality.VERIFIED_BIDIRECTIONAL -> "VERIFIED 2-WAY" to BadgeSeverity.SUCCESS
        RecordingQuality.MIXED_UNVERIFIED -> "MIXED UNVERIFIED" to BadgeSeverity.WARNING
        RecordingQuality.ONE_SIDED -> "1-SIDED ONLY" to BadgeSeverity.WARNING
        RecordingQuality.SILENT -> "SILENT" to BadgeSeverity.DANGER
        RecordingQuality.CORRUPT -> "CORRUPT" to BadgeSeverity.DANGER
        RecordingQuality.UNSUPPORTED -> "UNSUPPORTED" to BadgeSeverity.DANGER
        RecordingQuality.UNKNOWN -> "UNKNOWN" to BadgeSeverity.MUTED
    }
    StatusBadge(text = label, severity = severity, modifier = modifier)
}

@Composable
fun DownloadStatusBadge(status: DownloadStatus, modifier: Modifier = Modifier) {
    val (label, severity) = when (status) {
        DownloadStatus.IDLE -> "IDLE" to BadgeSeverity.MUTED
        DownloadStatus.PROBING -> "PROBING" to BadgeSeverity.INFO
        DownloadStatus.QUEUED -> "QUEUED" to BadgeSeverity.INFO
        DownloadStatus.DOWNLOADING -> "DOWNLOADING" to BadgeSeverity.WARNING
        DownloadStatus.POSTPROCESSING -> "PROCESSING" to BadgeSeverity.WARNING
        DownloadStatus.COMPLETED -> "STORED" to BadgeSeverity.SUCCESS
        DownloadStatus.FAILED -> "FAILED" to BadgeSeverity.DANGER
        DownloadStatus.CANCELLED -> "CANCELLED" to BadgeSeverity.MUTED
    }
    StatusBadge(text = label, severity = severity, modifier = modifier)
}
