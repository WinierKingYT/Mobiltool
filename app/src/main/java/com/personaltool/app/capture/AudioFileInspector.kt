package com.personaltool.app.capture

import android.media.MediaMetadataRetriever
import com.personaltool.core.model.call.RecordingQuality
import java.io.File
import java.io.FileInputStream

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

    const val MIN_VALID_FILE_SIZE_BYTES = 2048L
    const val MIN_VALID_DURATION_MS = 500L
    const val MIN_VALID_BITRATE_BPS = 8000L

    /**
     * Inspects a recorded audio file. Checks raw container magic bytes (ftyp atom for MP4/M4A),
     * minimum file size, parser validity, duration, and bitrate.
     */
    fun inspectRecordedFile(
        filePath: String,
        defaultQuality: RecordingQuality,
        captureTier: com.personaltool.core.model.call.CallCaptureTier = com.personaltool.core.model.call.CallCaptureTier.UNSUPPORTED_USERSPACE
    ): AudioFileInspectionResult {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            return AudioFileInspectionResult(
                isValid = false,
                durationMs = 0L,
                bitrate = 0L,
                mimeType = null,
                fileSizeBytes = if (file.exists()) file.length() else 0L,
                determinedQuality = RecordingQuality.CORRUPT,
                rejectionReason = "File does not exist or is unreadable."
            )
        }

        val fileSize = file.length()
        if (fileSize < MIN_VALID_FILE_SIZE_BYTES) {
            return AudioFileInspectionResult(
                isValid = false,
                durationMs = 0L,
                bitrate = 0L,
                mimeType = null,
                fileSizeBytes = fileSize,
                determinedQuality = RecordingQuality.CORRUPT,
                rejectionReason = "File size (${fileSize}B) is below minimum threshold (${MIN_VALID_FILE_SIZE_BYTES}B)."
            )
        }

        if (!isValidM4AContainerHeader(file)) {
            return AudioFileInspectionResult(
                isValid = false,
                durationMs = 0L,
                bitrate = 0L,
                mimeType = null,
                fileSizeBytes = fileSize,
                determinedQuality = RecordingQuality.CORRUPT,
                rejectionReason = "Corrupted container: Missing standard MP4/M4A ftyp header signature."
            )
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val bitrate = bitrateStr?.toLongOrNull() ?: 0L

            when {
                durationMs < MIN_VALID_DURATION_MS -> {
                    AudioFileInspectionResult(
                        isValid = false,
                        durationMs = durationMs,
                        bitrate = bitrate,
                        mimeType = mimeType,
                        fileSizeBytes = fileSize,
                        determinedQuality = RecordingQuality.SILENT,
                        rejectionReason = "Duration (${durationMs}ms) is below minimum threshold (${MIN_VALID_DURATION_MS}ms)."
                    )
                }
                bitrate > 0 && bitrate < MIN_VALID_BITRATE_BPS -> {
                    AudioFileInspectionResult(
                        isValid = false,
                        durationMs = durationMs,
                        bitrate = bitrate,
                        mimeType = mimeType,
                        fileSizeBytes = fileSize,
                        determinedQuality = RecordingQuality.CORRUPT,
                        rejectionReason = "Bitrate (${bitrate}bps) is suspiciously low (< ${MIN_VALID_BITRATE_BPS}bps)."
                    )
                }
                else -> {
                    // Quality Invariant: Only genuine PRIVILEGED_DIRECT and OEM_IMPORT yield VERIFIED_BIDIRECTIONAL
                    val safeQuality = when {
                        captureTier == com.personaltool.core.model.call.CallCaptureTier.PRIVILEGED_DIRECT ||
                                captureTier == com.personaltool.core.model.call.CallCaptureTier.OEM_IMPORT -> {
                            RecordingQuality.VERIFIED_BIDIRECTIONAL
                        }
                        defaultQuality == RecordingQuality.VERIFIED_BIDIRECTIONAL -> {
                            RecordingQuality.MIXED_UNVERIFIED
                        }
                        else -> defaultQuality
                    }
                    AudioFileInspectionResult(
                        isValid = true,
                        durationMs = durationMs,
                        bitrate = bitrate,
                        mimeType = mimeType,
                        fileSizeBytes = fileSize,
                        determinedQuality = safeQuality,
                        rejectionReason = null
                    )
                }
            }
        } catch (err: Exception) {
            AudioFileInspectionResult(
                isValid = false,
                durationMs = 0L,
                bitrate = 0L,
                mimeType = null,
                fileSizeBytes = fileSize,
                determinedQuality = RecordingQuality.CORRUPT,
                rejectionReason = "MediaMetadataRetriever decoding failed: ${err.message}"
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Verifies if the first bytes match ISO Base Media File Format (MP4/M4A 'ftyp' box).
     * Bytes 4..7 must equal "ftyp" (0x66, 0x74, 0x79, 0x70).
     */
    fun isValidM4AContainerHeader(file: File): Boolean {
        if (file.length() < 8) return false
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(8)
                val read = input.read(header)
                if (read < 8) return false
                header[4] == 'f'.code.toByte() &&
                        header[5] == 't'.code.toByte() &&
                        header[6] == 'y'.code.toByte() &&
                        header[7] == 'p'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }
}
