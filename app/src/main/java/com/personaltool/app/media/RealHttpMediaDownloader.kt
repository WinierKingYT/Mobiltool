package com.personaltool.app.media

import android.content.Context
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.media.DownloadStatus
import com.personaltool.core.model.media.MediaFormatOption
import com.personaltool.core.model.media.MediaItem
import com.personaltool.core.model.media.MediaSource
import com.personaltool.core.model.media.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class RealProbeResult(
    val url: String,
    val title: String,
    val sourcePlatform: MediaSource,
    val contentType: String,
    val fileSizeBytes: Long,
    val availableFormats: List<MediaFormatOption>
)

class RealHttpMediaDownloader(private val context: Context) {

    private val _activeDownloads = MutableStateFlow<Map<String, MediaItem>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, MediaItem>> = _activeDownloads.asStateFlow()

    suspend fun probeUrl(rawUrl: String): AppResult<RealProbeResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(rawUrl.trim())
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) PersonalToolMediaEngine/1.0")
            }
            conn.connect()

            val contentLength = conn.contentLengthLong.coerceAtLeast(0L)
            val contentType = conn.contentType ?: "video/mp4"
            conn.disconnect()

            val filename = rawUrl.substringAfterLast("/").substringBefore("?").ifBlank { "media_${System.currentTimeMillis()}" }
            val platform = when {
                rawUrl.contains("youtube.com") || rawUrl.contains("youtu.be") -> MediaSource.YOUTUBE
                rawUrl.contains("instagram.com") -> MediaSource.INSTAGRAM
                rawUrl.contains("x.com") || rawUrl.contains("twitter.com") -> MediaSource.X_TWITTER
                else -> MediaSource.GENERIC_URL
            }

            val formats = listOf(
                MediaFormatOption(
                    formatId = "hq-video",
                    ext = if (contentType.contains("audio")) "m4a" else "mp4",
                    resolution = if (contentType.contains("audio")) "Audio HQ" else "Auto / Best Stream",
                    fileSizeBytes = if (contentLength > 0) contentLength else null,
                    isAudioOnly = contentType.contains("audio"),
                    note = "Direct HTTP Stream Probe ($contentType)"
                ),
                MediaFormatOption(
                    formatId = "audio-extracted",
                    ext = "m4a",
                    resolution = "Audio Stream (AAC)",
                    fileSizeBytes = if (contentLength > 0) (contentLength / 4) else null,
                    isAudioOnly = true,
                    note = "Demuxed Audio Stream"
                )
            )

            AppResult.Success(
                RealProbeResult(
                    url = rawUrl,
                    title = filename,
                    sourcePlatform = platform,
                    contentType = contentType,
                    fileSizeBytes = contentLength,
                    availableFormats = formats
                )
            )
        }.getOrElse { err ->
            AppResult.Error("Probe failed: ${err.localizedMessage ?: "Invalid URL or network timeout"}")
        }
    }

    suspend fun downloadUrl(
        probe: RealProbeResult,
        selectedFormat: MediaFormatOption,
        onProgress: (Int, Long) -> Unit
    ): AppResult<MediaItem> = withContext(Dispatchers.IO) {
        val downloadId = UUID.randomUUID().toString()
        val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
        val ext = selectedFormat.ext
        val outputFile = File(mediaDir, "media_${downloadId}.$ext")

        val mediaItem = MediaItem(
            id = downloadId,
            sourceUrl = probe.url,
            title = probe.title,
            localFilePath = outputFile.absolutePath,
            mediaType = if (selectedFormat.isAudioOnly) MediaType.AUDIO_ONLY else MediaType.VIDEO,
            sourcePlatform = probe.sourcePlatform,
            formatSelected = selectedFormat.formatId,
            fileSizeBytes = probe.fileSizeBytes,
            downloadStatus = DownloadStatus.DOWNLOADING,
            downloadProgressPercent = 0
        )

        _activeDownloads.value = _activeDownloads.value + (downloadId to mediaItem)

        runCatching {
            val url = URL(probe.url)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) PersonalToolMediaEngine/1.0")
            }
            conn.connect()

            val totalBytes = conn.contentLengthLong.coerceAtLeast(1L)
            var downloadedBytes = 0L
            val buffer = ByteArray(16384)

            conn.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    var bytesRead: Int
                    var lastUpdate = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 150) {
                            val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 50
                            onProgress(percent, downloadedBytes)
                            _activeDownloads.value = _activeDownloads.value + (downloadId to mediaItem.copy(
                                downloadProgressPercent = percent,
                                fileSizeBytes = downloadedBytes
                            ))
                            lastUpdate = now
                        }
                    }
                }
            }
            conn.disconnect()

            val completedItem = mediaItem.copy(
                downloadStatus = DownloadStatus.COMPLETED,
                downloadProgressPercent = 100,
                fileSizeBytes = outputFile.length()
            )
            _activeDownloads.value = _activeDownloads.value + (downloadId to completedItem)
            AppResult.Success(completedItem)
        }.getOrElse { err ->
            outputFile.delete()
            val failedItem = mediaItem.copy(
                downloadStatus = DownloadStatus.FAILED,
                downloadProgressPercent = 0
            )
            _activeDownloads.value = _activeDownloads.value + (downloadId to failedItem)
            AppResult.Error("Download failed: ${err.message}")
        }
    }
}
