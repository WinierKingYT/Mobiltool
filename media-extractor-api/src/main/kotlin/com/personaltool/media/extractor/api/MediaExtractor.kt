package com.personaltool.media.extractor.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.media.MediaFormatOption
import com.personaltool.core.model.media.MediaSource
import com.personaltool.core.model.media.MediaType

data class MediaProbeResult(
    val url: String,
    val title: String,
    val uploader: String? = null,
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
    val sourcePlatform: MediaSource = MediaSource.GENERIC_URL,
    val availableFormats: List<MediaFormatOption> = emptyList(),
    val isDrmProtected: Boolean = false,
    val requiresAuthentication: Boolean = false
)

data class DownloadRequest(
    val id: String,
    val sourceUrl: String,
    val formatId: String,
    val destinationPath: String,
    val targetType: MediaType = MediaType.VIDEO
)

data class DownloadProgress(
    val downloadId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val percent: Int,
    val speedBytesPerSec: Long = 0L
)

data class DownloadedMediaResult(
    val downloadId: String,
    val outputFilePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val mimeType: String,
    val mediaKind: DetectedMediaKind = DetectedMediaKind.UNKNOWN
)

interface MediaExtractor {
    val adapterName: String

    fun canHandle(url: String): Boolean

    suspend fun probeUrl(url: String): AppResult<MediaProbeResult>

    suspend fun downloadMedia(
        request: DownloadRequest,
        onProgress: (DownloadProgress) -> Unit
    ): AppResult<DownloadedMediaResult>

    suspend fun cancelDownload(downloadId: String): AppResult<Unit>
}
