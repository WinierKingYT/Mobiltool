package com.personaltool.app.viewmodel

import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.media.DownloadStatus
import com.personaltool.core.model.media.MediaItem
import com.personaltool.core.model.media.MediaType
import com.personaltool.media.extractor.api.MediaFileValidator
import java.io.File
import java.io.FileInputStream

enum class VaultFileState {
    AVAILABLE,
    NOT_READY,
    NO_LOCAL_FILE,
    MISSING,
    UNREADABLE,
    SIZE_MISMATCH,
    INVALID_MEDIA,
    UNKNOWN
}

enum class VaultPrimaryAction {
    PLAY_AUDIO,
    PLAY_VIDEO,
    OPEN_TRANSCRIPT,
    UNAVAILABLE
}

sealed interface VaultItem {
    val id: String
    val title: String
    val createdAt: Long
    val hasTranscript: Boolean
    val fileState: VaultFileState
    val primaryAction: VaultPrimaryAction
    val availableSizeBytes: Long

    data class Call(
        val session: CallSession,
        override val fileState: VaultFileState,
        override val primaryAction: VaultPrimaryAction,
        override val availableSizeBytes: Long = 0L
    ) : VaultItem {
        override val id: String = session.id
        override val title: String = session.contactName ?: session.phoneNumber
        override val createdAt: Long = session.createdAt
        override val hasTranscript: Boolean = session.hasTranscript
    }

    data class Media(
        val item: MediaItem,
        override val fileState: VaultFileState,
        override val primaryAction: VaultPrimaryAction,
        override val availableSizeBytes: Long = 0L
    ) : VaultItem {
        override val id: String = item.id
        override val title: String = item.title
        override val createdAt: Long = item.createdAt
        override val hasTranscript: Boolean = item.hasTranscript
    }
}

interface VaultItemEvaluator {
    fun evaluateCall(session: CallSession): VaultItem.Call
    fun evaluateMedia(item: MediaItem): VaultItem.Media
}

object VaultFileAvailabilityInspector {

    const val MIN_CALL_SIZE_BYTES = 2048L
    const val MIN_MEDIA_SIZE_BYTES = 1024L
    const val HEADER_INSPECTION_BYTES = 768

    fun inspectCallFile(file: File, expectedSizeBytes: Long): VaultFileState {
        if (!file.exists()) {
            return VaultFileState.MISSING
        }

        val ext = file.extension.lowercase()
        if (ext == "part" || ext == "tmp" || file.name.endsWith(".part") || file.name.endsWith(".tmp")) {
            return VaultFileState.INVALID_MEDIA
        }

        if (!file.isFile || !file.canRead()) {
            return VaultFileState.UNREADABLE
        }

        val actualLength = file.length()
        if (expectedSizeBytes > 0L && actualLength != expectedSizeBytes) {
            return VaultFileState.SIZE_MISMATCH
        }

        if (actualLength < MIN_CALL_SIZE_BYTES) {
            return VaultFileState.INVALID_MEDIA
        }

        // Bounded prefix inspection (<= 768 bytes, no full-file SHA calculation)
        val header = ByteArray(HEADER_INSPECTION_BYTES)
        val bytesRead = try {
            FileInputStream(file).use { it.read(header) }
        } catch (_: Exception) {
            return VaultFileState.UNREADABLE
        }

        if (bytesRead < 8) {
            return VaultFileState.INVALID_MEDIA
        }

        val inspection = MediaFileValidator.inspectHeaderBytes(header, bytesRead)
        return when (inspection) {
            is MediaFileValidator.HeaderValidationResult.ValidMedia -> VaultFileState.AVAILABLE
            is MediaFileValidator.HeaderValidationResult.Invalid -> VaultFileState.INVALID_MEDIA
        }
    }

    fun inspectMediaFile(file: File, expectedSizeBytes: Long, downloadStatus: DownloadStatus): VaultFileState {
        if (!file.exists()) {
            return VaultFileState.MISSING
        }

        if (downloadStatus != DownloadStatus.COMPLETED) {
            return VaultFileState.NOT_READY
        }

        val ext = file.extension.lowercase()
        if (ext == "part" || ext == "tmp" || file.name.endsWith(".part") || file.name.endsWith(".tmp")) {
            return VaultFileState.INVALID_MEDIA
        }

        if (!file.isFile || !file.canRead()) {
            return VaultFileState.UNREADABLE
        }

        val actualLength = file.length()
        if (expectedSizeBytes > 0L && actualLength != expectedSizeBytes) {
            return VaultFileState.SIZE_MISMATCH
        }

        if (actualLength < MIN_MEDIA_SIZE_BYTES) {
            return VaultFileState.INVALID_MEDIA
        }

        // Bounded prefix inspection (<= 768 bytes, no full-file SHA calculation)
        val header = ByteArray(HEADER_INSPECTION_BYTES)
        val bytesRead = try {
            FileInputStream(file).use { it.read(header) }
        } catch (_: Exception) {
            return VaultFileState.UNREADABLE
        }

        if (bytesRead < 8) {
            return VaultFileState.INVALID_MEDIA
        }

        val inspection = MediaFileValidator.inspectHeaderBytes(header, bytesRead)
        return when (inspection) {
            is MediaFileValidator.HeaderValidationResult.ValidMedia -> VaultFileState.AVAILABLE
            is MediaFileValidator.HeaderValidationResult.Invalid -> VaultFileState.INVALID_MEDIA
        }
    }
}

class DefaultVaultItemEvaluator(
    private val fileResolver: (String) -> File = { File(it) }
) : VaultItemEvaluator {

    override fun evaluateCall(session: CallSession): VaultItem.Call {
        val path = session.audioFilePath
        val fileState = evaluateCallFileState(session, path)
        val primaryAction = resolveCallPrimaryAction(session, fileState)
        val availableBytes = if (fileState == VaultFileState.AVAILABLE && path != null) {
            val file = fileResolver(path)
            if (file.exists()) file.length() else 0L
        } else {
            0L
        }

        return VaultItem.Call(
            session = session,
            fileState = fileState,
            primaryAction = primaryAction,
            availableSizeBytes = availableBytes
        )
    }

    override fun evaluateMedia(item: MediaItem): VaultItem.Media {
        val path = item.localFilePath
        val fileState = evaluateMediaFileState(item, path)
        val primaryAction = resolveMediaPrimaryAction(item, fileState)
        val availableBytes = if (fileState == VaultFileState.AVAILABLE && path != null) {
            val file = fileResolver(path)
            if (file.exists()) file.length() else 0L
        } else {
            0L
        }

        return VaultItem.Media(
            item = item,
            fileState = fileState,
            primaryAction = primaryAction,
            availableSizeBytes = availableBytes
        )
    }

    private fun evaluateCallFileState(session: CallSession, path: String?): VaultFileState {
        if (path.isNullOrBlank()) {
            return VaultFileState.NO_LOCAL_FILE
        }

        val file = fileResolver(path)
        return VaultFileAvailabilityInspector.inspectCallFile(file, session.fileSizeBytes)
    }

    private fun evaluateMediaFileState(item: MediaItem, path: String?): VaultFileState {
        if (path.isNullOrBlank()) {
            return VaultFileState.NO_LOCAL_FILE
        }

        val file = fileResolver(path)
        return VaultFileAvailabilityInspector.inspectMediaFile(file, item.fileSizeBytes, item.downloadStatus)
    }

    private fun resolveCallPrimaryAction(session: CallSession, fileState: VaultFileState): VaultPrimaryAction {
        return when {
            fileState == VaultFileState.AVAILABLE && !session.audioFilePath.isNullOrBlank() -> {
                VaultPrimaryAction.PLAY_AUDIO
            }
            session.hasTranscript -> {
                VaultPrimaryAction.OPEN_TRANSCRIPT
            }
            else -> {
                VaultPrimaryAction.UNAVAILABLE
            }
        }
    }

    private fun resolveMediaPrimaryAction(item: MediaItem, fileState: VaultFileState): VaultPrimaryAction {
        if (fileState != VaultFileState.AVAILABLE) {
            return VaultPrimaryAction.UNAVAILABLE
        }

        return when (item.mediaType) {
            MediaType.AUDIO_ONLY -> VaultPrimaryAction.PLAY_AUDIO
            MediaType.VIDEO -> VaultPrimaryAction.PLAY_VIDEO
            else -> VaultPrimaryAction.UNAVAILABLE
        }
    }
}
