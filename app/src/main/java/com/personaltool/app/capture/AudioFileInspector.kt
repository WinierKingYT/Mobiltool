package com.personaltool.app.capture

import android.media.MediaMetadataRetriever
import com.personaltool.core.model.call.RecordingQuality
import java.io.File

data class AudioFileInspectionResult(
    val isValid: Boolean,
    val durationMs: Long,
    val bitrate: Long,
    val mimeType: String?,
    val fileSizeBytes: Long,
    val determinedQuality: RecordingQuality,
    val rejectionReason: String? = null
)

object AudioFileInspector {

    private const val MIN_VALID_FILE_SIZE_BYTES = 2048L
    private const val MIN_VALID_DURATION_MS = 500L

    fun inspectRecordedFile(filePath: String, defaultQuality: RecordingQuality): AudioFileInspectionResult {
        val file = File(filePath)
        if (!file.exists() || file.length() < MIN_VALID_FILE_SIZE_BYTES) {
            return AudioFileInspectionResult(
                isValid = false,
                durationMs = 0L,
                bitrate = 0L,
                mimeType = null,
                fileSizeBytes = if (file.exists()) file.length() else 0L,
                determinedQuality = RecordingQuality.CORRUPT,
                rejectionReason = "File missing or smaller than 2KB container threshold."
            )
        }

        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val bitrate = bitrateStr?.toLongOrNull() ?: 0L

            if (durationMs < MIN_VALID_DURATION_MS) {
                AudioFileInspectionResult(
                    isValid = false,
                    durationMs = durationMs,
                    bitrate = bitrate,
                    mimeType = mimeType,
                    fileSizeBytes = file.length(),
                    determinedQuality = RecordingQuality.SILENT,
                    rejectionReason = "Duration less than minimum 500ms threshold."
                )
            } else {
                AudioFileInspectionResult(
                    isValid = true,
                    durationMs = durationMs,
                    bitrate = bitrate,
                    mimeType = mimeType,
                    fileSizeBytes = file.length(),
                    determinedQuality = defaultQuality,
                    rejectionReason = null
                )
            }
        }.getOrElse { err ->
            AudioFileInspectionResult(
                isValid = false,
                durationMs = 0L,
                bitrate = 0L,
                mimeType = null,
                fileSizeBytes = file.length(),
                determinedQuality = RecordingQuality.CORRUPT,
                rejectionReason = "Media parser failed: ${err.message}"
            )
        }.also {
            runCatching { retriever.release() }
        }
    }
}
