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
            MediaSource.YOUTUBE,
            MediaSource.INSTAGRAM,
            MediaSource.X_TWITTER -> {
                // Truth Gate: Specialized platform scrapers (YouTube/IG/X) are not implemented/linked in P0.
                // Fail closed with stable error code instead of fabricating metadata or formats.
                AppResult.Error(
                    message = "PLATFORM_EXTRACTION_UNAVAILABLE: Dedicated extractor for ${platform.name} is not linked in P0 baseline. Direct stream extraction only.",
                    code = ErrorCode.EXTRACTION_FAILED
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
