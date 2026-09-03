package com.personaltool.app.viewmodel

import com.personaltool.app.capture.AudioFileInspector
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.media.DownloadStatus
import com.personaltool.core.model.media.MediaItem
import com.personaltool.core.model.media.MediaType
import com.personaltool.media.extractor.api.FileValidationResult
import com.personaltool.media.extractor.api.MediaFileValidator
import com.personaltool.media.extractor.api.ValidationContext
import java.io.File

enum class VaultFileState {
    AVAILABLE,
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
        if (!file.exists()) {
            return VaultFileState.MISSING
        }

        if (!file.isFile || !file.canRead()) {
            return VaultFileState.UNREADABLE
        }

        if (session.fileSizeBytes > 0L && file.length() != session.fileSizeBytes) {
            return VaultFileState.SIZE_MISMATCH
        }

        if (file.length() < 8L) {
            return VaultFileState.INVALID_MEDIA
        }

        val validationResult = MediaFileValidator.validateFile(file, ValidationContext.CANONICAL_MEDIA)
        return when (validationResult) {
            is FileValidationResult.Valid -> VaultFileState.AVAILABLE
            is FileValidationResult.Invalid -> {
                if (AudioFileInspector.isValidM4AContainerHeader(file)) {
                    VaultFileState.AVAILABLE
                } else {
                    VaultFileState.INVALID_MEDIA
                }
            }
        }
    }

    private fun evaluateMediaFileState(item: MediaItem, path: String?): VaultFileState {
        if (path.isNullOrBlank()) {
            return VaultFileState.NO_LOCAL_FILE
        }

        val file = fileResolver(path)
        if (!file.exists()) {
            return VaultFileState.MISSING
        }

        if (item.downloadStatus != DownloadStatus.COMPLETED) {
            return VaultFileState.UNREADABLE
        }

        if (!file.isFile || !file.canRead()) {
            return VaultFileState.UNREADABLE
        }

        if (item.fileSizeBytes > 0L && file.length() != item.fileSizeBytes) {
            return VaultFileState.SIZE_MISMATCH
        }

        val validationResult = MediaFileValidator.validateFile(file, ValidationContext.CANONICAL_MEDIA)
        return when (validationResult) {
            is FileValidationResult.Valid -> VaultFileState.AVAILABLE
            is FileValidationResult.Invalid -> VaultFileState.INVALID_MEDIA
        }
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
