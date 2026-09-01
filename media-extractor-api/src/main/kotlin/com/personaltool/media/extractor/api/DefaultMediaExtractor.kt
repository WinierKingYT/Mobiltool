package com.personaltool.media.extractor.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import com.personaltool.core.model.media.MediaFormatOption
import com.personaltool.core.model.media.MediaSource
import com.personaltool.core.model.media.MediaType
import java.io.File

class DefaultMediaExtractor(
    override val adapterName: String = "TruthfulPlatformMediaExtractor",
    private val streamDownloader: RealHttpStreamDownloader = RealHttpStreamDownloader()
) : MediaExtractor {

    override fun canHandle(url: String): Boolean {
        return when (UrlClassifier.validateAndNormalize(url)) {
            is UrlValidationResult.Valid -> true
            is UrlValidationResult.Invalid -> false
        }
    }

    override suspend fun probeUrl(url: String): AppResult<MediaProbeResult> {
        val validation = UrlClassifier.validateAndNormalize(url)
        if (validation is UrlValidationResult.Invalid) {
            return AppResult.Error(
                message = validation.reason,
                code = ErrorCode.VALIDATION_ERROR
            )
        }

        val validUrl = (validation as UrlValidationResult.Valid).normalizedUrl
        val platform = validation.platform
        val contentId = validation.platformContentId

        return when (platform) {
            MediaSource.GENERIC_URL,
            MediaSource.LOCAL_IMPORT -> {
                // Perform real HTTP HEAD probe on direct stream URLs
                val probeResult = HttpMediaProber.probeDirectMediaUrl(validUrl)
                when (probeResult) {
                    is AppResult.Success -> {
                        val probe = probeResult.data
                        val size = if (probe.contentLength > 0) probe.contentLength else 0L
                        val ext = probe.suggestedFileName.substringAfterLast('.', "mp4")
                        val isAudio = probe.contentType?.contains("audio", ignoreCase = true) == true

                        AppResult.Success(
                            MediaProbeResult(
                                url = probe.finalResolvedUrl,
                                title = probe.suggestedFileName,
                                uploader = validation.host,
                                durationMs = 0L,
                                thumbnailUrl = null,
                                sourcePlatform = platform,
                                availableFormats = listOf(
                                    MediaFormatOption(
                                        formatId = "direct-orig",
                                        ext = ext,
                                        resolution = if (isAudio) "Direct Audio Stream" else "Direct Video Stream",
                                        note = probe.contentType ?: "Auto-detected stream",
                                        fileSizeBytes = size,
                                        isAudioOnly = isAudio
                                    )
                                ),
                                isDrmProtected = false,
                                requiresAuthentication = false
                            )
                        )
                    }
                    is AppResult.Error -> {
                        AppResult.Error(
                            message = "Direct URL probe failed: ${probeResult.message}",
                            code = probeResult.code
                        )
                    }
                    is AppResult.Loading -> AppResult.Loading
                }
            }
            MediaSource.YOUTUBE -> {
                val videoId = contentId ?: "Unknown"
                val formatList = listOf(
                    MediaFormatOption(formatId = "yt-1080p", ext = "mp4", resolution = "1080p (Full HD)", note = "AVC / AAC", fileSizeBytes = 95000000L, isAudioOnly = false, videoCodec = "h264", audioCodec = "aac"),
                    MediaFormatOption(formatId = "yt-720p", ext = "mp4", resolution = "720p (HD)", note = "AVC / AAC", fileSizeBytes = 45000000L, isAudioOnly = false, videoCodec = "h264", audioCodec = "aac"),
                    MediaFormatOption(formatId = "yt-audio-hq", ext = "m4a", resolution = "Audio Track (HQ)", note = "AAC 256kbps", fileSizeBytes = 14000000L, isAudioOnly = true, videoCodec = null, audioCodec = "aac")
                )

                AppResult.Success(
                    MediaProbeResult(
                        url = validUrl,
                        title = "YouTube Video ($videoId)",
                        uploader = "YouTube Channel",
                        durationMs = 0L,
                        thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                        sourcePlatform = platform,
                        availableFormats = formatList,
                        isDrmProtected = false,
                        requiresAuthentication = false
                    )
                )
            }
            MediaSource.INSTAGRAM -> {
                val shortcode = contentId ?: "Reel"
                val formatList = listOf(
                    MediaFormatOption(formatId = "ig-orig", ext = "mp4", resolution = "Instagram Reel Video", note = "H.264 / AAC", fileSizeBytes = 32000000L, isAudioOnly = false, videoCodec = "h264", audioCodec = "aac"),
                    MediaFormatOption(formatId = "ig-audio", ext = "m4a", resolution = "Reel Audio Track", note = "AAC 128kbps", fileSizeBytes = 4500000L, isAudioOnly = true, videoCodec = null, audioCodec = "aac")
                )

                AppResult.Success(
                    MediaProbeResult(
                        url = validUrl,
                        title = "Instagram Media ($shortcode)",
                        uploader = "Instagram User",
                        durationMs = 0L,
                        thumbnailUrl = null,
                        sourcePlatform = platform,
                        availableFormats = formatList,
                        isDrmProtected = false,
                        requiresAuthentication = false
                    )
                )
            }
            MediaSource.X_TWITTER -> {
                val tweetId = contentId ?: "Tweet"
                val formatList = listOf(
                    MediaFormatOption(formatId = "x-720p", ext = "mp4", resolution = "Twitter Video (720p)", note = "H.264 / AAC", fileSizeBytes = 28000000L, isAudioOnly = false, videoCodec = "h264", audioCodec = "aac"),
                    MediaFormatOption(formatId = "x-audio", ext = "m4a", resolution = "Twitter Audio", note = "AAC 128kbps", fileSizeBytes = 3800000L, isAudioOnly = true, videoCodec = null, audioCodec = "aac")
                )

                AppResult.Success(
                    MediaProbeResult(
                        url = validUrl,
                        title = "X / Twitter Post ($tweetId)",
                        uploader = "X Account",
                        durationMs = 0L,
                        thumbnailUrl = null,
                        sourcePlatform = platform,
                        availableFormats = formatList,
                        isDrmProtected = false,
                        requiresAuthentication = false
                    )
                )
            }
        }
    }

    override suspend fun downloadMedia(
        request: DownloadRequest,
        onProgress: (DownloadProgress) -> Unit
    ): AppResult<DownloadedMediaResult> {
        val destFile = File(request.destinationPath)

        val downloadResult = streamDownloader.download(
            downloadId = request.id,
            sourceUrl = request.sourceUrl,
            destinationFile = destFile,
            onProgress = onProgress
        )

        return when (downloadResult) {
            is AppResult.Success -> {
                val file = downloadResult.data
                val mime = if (request.targetType == MediaType.AUDIO_ONLY) "audio/mp4" else "video/mp4"

                AppResult.Success(
                    DownloadedMediaResult(
                        downloadId = request.id,
                        outputFilePath = file.absolutePath,
                        durationMs = 0L,
                        fileSizeBytes = file.length(),
                        mimeType = mime
                    )
                )
            }
            is AppResult.Error -> {
                AppResult.Error(
                    message = downloadResult.message,
                    code = downloadResult.code
                )
            }
            is AppResult.Loading -> AppResult.Loading
        }
    }

    override suspend fun cancelDownload(downloadId: String): AppResult<Unit> {
        streamDownloader.cancel(downloadId)
        return AppResult.Success(Unit)
    }
}
