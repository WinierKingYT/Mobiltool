package com.personaltool.media.extractor.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import com.personaltool.core.model.media.MediaFormatOption
import com.personaltool.core.model.media.MediaSource
import java.io.File

class DefaultMediaExtractor(
    override val adapterName: String = "TruthfulPlatformMediaExtractor",
    private val streamDownloader: RealHttpStreamDownloader = RealHttpStreamDownloader(),
    private val dnsLookup: DnsLookup = SystemDnsLookup
) : MediaExtractor {

    override fun canHandle(url: String): Boolean {
        return when (UrlClassifier.validateAndNormalize(url, dnsLookup)) {
            is UrlValidationResult.Valid -> true
            is UrlValidationResult.Invalid -> false
        }
    }

    override suspend fun probeUrl(url: String): AppResult<MediaProbeResult> {
        val validation = UrlClassifier.validateAndNormalize(url, dnsLookup)
        if (validation is UrlValidationResult.Invalid) {
            return AppResult.Error(
                message = validation.reason,
                code = if (validation.isSsrfViolation) ErrorCode.SECURITY_VIOLATION else ErrorCode.VALIDATION_ERROR
            )
        }

        val valid = validation as UrlValidationResult.Valid
        val platform = valid.platform

        return when (platform) {
            MediaSource.GENERIC_URL,
            MediaSource.LOCAL_IMPORT -> {
                val probeResult = HttpMediaProber.probeDirectMediaUrl(valid.normalizedUrl, dnsLookup)
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
                                uploader = valid.host,
                                durationMs = 0L,
                                thumbnailUrl = null,
                                sourcePlatform = platform,
                                availableFormats = listOf(
                                    MediaFormatOption(
                                        formatId = "direct-orig",
                                        ext = ext,
                                        resolution = if (isAudio) "Direct Audio Stream" else "Direct Stream",
                                        note = probe.contentType ?: "Direct HTTP Media",
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
                // Truth Gate: Platform extractors are unlinked pending ADR_002 approval.
                AppResult.Error(
                    message = "PLATFORM_EXTRACTION_UNAVAILABLE: Dedicated extractor for ${platform.name} is unlinked in current baseline. Direct HTTP streams only.",
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
                val fileInfo = downloadResult.data
                AppResult.Success(
                    DownloadedMediaResult(
                        downloadId = request.id,
                        outputFilePath = fileInfo.file.absolutePath,
                        durationMs = 0L,
                        fileSizeBytes = fileInfo.fileSizeBytes,
                        mimeType = fileInfo.detectedMimeType,
                        mediaKind = fileInfo.mediaKind
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
