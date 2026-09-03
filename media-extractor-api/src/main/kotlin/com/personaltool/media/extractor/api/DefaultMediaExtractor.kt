package com.personaltool.media.extractor.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import com.personaltool.core.model.media.MediaFormatOption
import com.personaltool.core.model.media.MediaSource
import com.personaltool.media.extractor.api.youtube.NewPipeYouTubeExtractor
import com.personaltool.media.extractor.api.youtube.YouTubeExtractor
import java.io.File

class DefaultMediaExtractor(
    override val adapterName: String = "TruthfulPlatformMediaExtractor",
    private val dnsLookup: DnsLookup = SystemDnsLookup,
    private val streamDownloader: RealHttpStreamDownloader = RealHttpStreamDownloader(dnsLookup = dnsLookup),
    private val youtubeExtractor: YouTubeExtractor = NewPipeYouTubeExtractor(dnsLookup = dnsLookup)
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
                        val size = if (probe.contentLength > 0L) probe.contentLength else null
                        val ext = probe.provenExtension
                        val isAudio = probe.mediaKind == DetectedMediaKind.AUDIO
                        val isVideo = probe.mediaKind == DetectedMediaKind.VIDEO
                        val resolutionLabel = when {
                            isAudio -> "Direct Audio Stream"
                            isVideo -> "Direct Video Stream"
                            else -> "Direct Media Stream"
                        }

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
                                        resolution = resolutionLabel,
                                        note = probe.contentType ?: probe.verifiedMimeType ?: "Direct HTTP Media",
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
                // ADR_002 APPROVED: Pure JVM YouTube Extractor Adapter
                youtubeExtractor.probeYouTubeUrl(valid.normalizedUrl)
            }
            MediaSource.INSTAGRAM,
            MediaSource.X_TWITTER -> {
                // ADR_002: Instagram and X are NOT approved / unresolved in current baseline
                AppResult.Error(
                    message = "PLATFORM_EXTRACTION_UNAVAILABLE: Dedicated extractor for ${platform.name} is not approved in current baseline. Direct HTTP streams and YouTube only.",
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

        val validation = UrlClassifier.validateAndNormalize(request.sourceUrl, dnsLookup)
        var resolvedFormatId: String? = null

        val directStreamUrl = if (validation is UrlValidationResult.Valid && validation.platform == MediaSource.YOUTUBE) {
            // P2-YT-FINAL-02: Extract exact direct media stream from NewPipe
            when (val extractResult = youtubeExtractor.extractStream(validation.normalizedUrl, request.formatId)) {
                is AppResult.Success -> {
                    val stream = extractResult.data
                    resolvedFormatId = stream.formatId
                    stream.directStreamUrl
                }
                is AppResult.Error -> return AppResult.Error(extractResult.message, extractResult.cause, extractResult.code)
                AppResult.Loading -> return AppResult.Loading
            }
        } else {
            resolvedFormatId = request.formatId
            request.sourceUrl
        }

        // P2-YT-E04: All actual binary streaming continues strictly through Mobiltool's verified RealHttpStreamDownloader
        val downloadResult = streamDownloader.download(
            downloadId = request.id,
            sourceUrl = directStreamUrl,
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
                        mediaKind = fileInfo.mediaKind,
                        sha256Hex = fileInfo.sha256Hex,
                        commitMethod = fileInfo.commitMethod,
                        requestedFormatId = request.formatId,
                        resolvedFormatId = resolvedFormatId
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
