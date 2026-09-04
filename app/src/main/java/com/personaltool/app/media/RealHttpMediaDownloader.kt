package com.personaltool.app.media

import android.content.Context
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.media.DownloadStatus
import com.personaltool.core.model.media.MediaFormatOption
import com.personaltool.core.model.media.MediaItem
import com.personaltool.core.model.media.MediaSource
import com.personaltool.core.model.media.MediaType
import com.personaltool.media.extractor.api.DefaultMediaExtractor
import com.personaltool.media.extractor.api.DownloadRequest
import com.personaltool.media.extractor.api.DownloadedMediaResult
import com.personaltool.media.extractor.api.MediaProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class RealProbeResult(
    val url: String,
    val title: String,
    val sourcePlatform: MediaSource,
    val contentType: String,
    val fileSizeBytes: Long,
    val availableFormats: List<MediaFormatOption>
)

interface MediaIntakeDownloader {
    suspend fun probeUrl(rawUrl: String): AppResult<RealProbeResult>
    suspend fun downloadUrl(
        probe: RealProbeResult,
        selectedFormat: MediaFormatOption,
        onProgress: (Int, Long) -> Unit
    ): AppResult<MediaItem>
}

class RealHttpMediaDownloader(private val context: Context) : MediaIntakeDownloader {

    private val extractor = DefaultMediaExtractor()

    override suspend fun probeUrl(rawUrl: String): AppResult<RealProbeResult> = withContext(Dispatchers.IO) {
        when (val result = extractor.probeUrl(rawUrl)) {
            is AppResult.Success -> {
                val probe: MediaProbeResult = result.data
                val primaryFormat = probe.availableFormats.firstOrNull()
                AppResult.Success(
                    RealProbeResult(
                        url = probe.url,
                        title = probe.title,
                        sourcePlatform = probe.sourcePlatform,
                        contentType = primaryFormat?.note ?: "video/mp4",
                        fileSizeBytes = primaryFormat?.fileSizeBytes ?: 0L,
                        availableFormats = probe.availableFormats
                    )
                )
            }
            is AppResult.Error -> {
                AppResult.Error(result.message, result.cause, result.code)
            }
            AppResult.Loading -> AppResult.Loading
        }
    }

    override suspend fun downloadUrl(
        probe: RealProbeResult,
        selectedFormat: MediaFormatOption,
        onProgress: (Int, Long) -> Unit
    ): AppResult<MediaItem> = withContext(Dispatchers.IO) {
        val downloadId = UUID.randomUUID().toString()
        val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
        val ext = selectedFormat.ext
        val fileName = if (!ext.isNullOrBlank()) "media_${downloadId}.$ext" else "media_${downloadId}"
        val outputFile = File(mediaDir, fileName)

        val isAudio = selectedFormat.isAudioOnly
        val request = DownloadRequest(
            id = downloadId,
            sourceUrl = probe.url,
            formatId = selectedFormat.formatId,
            destinationPath = outputFile.absolutePath,
            targetType = if (isAudio) MediaType.AUDIO_ONLY else MediaType.VIDEO
        )

        when (val result = extractor.downloadMedia(request) { progress ->
            onProgress(progress.percent, progress.bytesDownloaded)
        }) {
            is AppResult.Success -> {
                val mediaResult: DownloadedMediaResult = result.data
                val completedFile = File(mediaResult.outputFilePath)

                val completedItem = MediaItem(
                    id = downloadId,
                    sourceUrl = probe.url,
                    title = probe.title,
                    localFilePath = completedFile.absolutePath,
                    mediaType = if (isAudio) MediaType.AUDIO_ONLY else MediaType.VIDEO,
                    sourcePlatform = probe.sourcePlatform,
                    formatSelected = selectedFormat.formatId,
                    resolution = selectedFormat.resolution,
                    fileSizeBytes = mediaResult.fileSizeBytes,
                    downloadStatus = DownloadStatus.COMPLETED,
                    downloadProgressPercent = 100
                )
                AppResult.Success(completedItem)
            }
            is AppResult.Error -> {
                outputFile.delete()
                AppResult.Error(result.message, result.cause, result.code)
            }
            AppResult.Loading -> AppResult.Loading
        }
    }
}
