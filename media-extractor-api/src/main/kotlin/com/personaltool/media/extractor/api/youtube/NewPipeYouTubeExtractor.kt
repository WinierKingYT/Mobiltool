package com.personaltool.media.extractor.api.youtube

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import com.personaltool.core.model.media.MediaFormatOption
import com.personaltool.core.model.media.MediaSource
import com.personaltool.media.extractor.api.DnsLookup
import com.personaltool.media.extractor.api.MediaProbeResult
import com.personaltool.media.extractor.api.SystemDnsLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * YouTube Extractor Adapter using NewPipeExtractor (P2-YT-FINAL-01..03, P2-TRUTH-LOCK-02).
 *
 * Invariants:
 * 1. Process-global runtime initialized via NewPipeRuntime with secured NewPipeDownloaderBridge.
 * 2. Exact format identity based strictly on valid upstream itags (P2-TRUTH-LOCK-02). Streams with itag <= 0 are omitted.
 * 3. Zero silent fallback streams (no firstAudio/firstVideo/anyStream substitutions).
 * 4. Zero leak of NewPipe internal types across adapter boundary.
 */
class NewPipeYouTubeExtractor(
    private val dnsLookup: DnsLookup = SystemDnsLookup,
    private val downloaderBridge: NewPipeDownloaderBridge = NewPipeDownloaderBridge(dnsLookup = dnsLookup)
) : YouTubeExtractor {

    companion object {
        fun normalizeYouTubeUrl(rawUrl: String): String {
            return when {
                rawUrl.contains("/shorts/") -> {
                    val id = rawUrl.substringAfter("/shorts/").substringBefore('?').substringBefore('/')
                    "https://www.youtube.com/watch?v=$id"
                }
                rawUrl.contains("youtu.be/") -> {
                    val id = rawUrl.substringAfter("youtu.be/").substringBefore('?').substringBefore('/')
                    "https://www.youtube.com/watch?v=$id"
                }
                else -> rawUrl
            }
        }
    }

    override suspend fun probeYouTubeUrl(url: String): AppResult<MediaProbeResult> = withContext(Dispatchers.IO) {
        try {
            NewPipeRuntime.ensureInitialized(downloaderBridge)

            val targetUrl = normalizeYouTubeUrl(url)
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, targetUrl)

            val formats = mutableListOf<MediaFormatOption>()

            // 1. Video Streams: only expose streams with valid upstream itags (P2-TRUTH-LOCK-02)
            streamInfo.videoStreams?.forEach { vStream ->
                val itag = vStream.itag
                if (itag > 0) {
                    val res = vStream.resolution ?: "Video"
                    val fmtName = vStream.format?.name ?: "mp4"
                    val ext = vStream.format?.suffix ?: "mp4"
                    val formatId = "youtube:video:itag:$itag"

                    formats.add(
                        MediaFormatOption(
                            formatId = formatId,
                            ext = ext,
                            resolution = res,
                            note = "YouTube Video ($res $fmtName itag:$itag)",
                            fileSizeBytes = null,
                            isAudioOnly = false,
                            videoCodec = vStream.codec,
                            audioCodec = null
                        )
                    )
                }
            }

            // 2. Audio Streams: only expose streams with valid upstream itags (P2-TRUTH-LOCK-02)
            streamInfo.audioStreams?.forEach { aStream ->
                val itag = aStream.itag
                if (itag > 0) {
                    val fmtName = aStream.format?.name ?: "m4a"
                    val ext = aStream.format?.suffix ?: "m4a"
                    val bitrate = aStream.averageBitrate
                    val resLabel = if (bitrate > 0) "$bitrate kbps Audio" else "Audio Stream"
                    val formatId = "youtube:audio:itag:$itag"

                    formats.add(
                        MediaFormatOption(
                            formatId = formatId,
                            ext = ext,
                            resolution = resLabel,
                            note = "YouTube Audio ($fmtName itag:$itag)",
                            fileSizeBytes = null,
                            isAudioOnly = true,
                            videoCodec = null,
                            audioCodec = aStream.codec
                        )
                    )
                }
            }

            if (formats.isEmpty()) {
                return@withContext AppResult.Error(
                    message = "PLATFORM_EXTRACTION_UNAVAILABLE: No playable public video or audio streams with stable itag identifiers found for YouTube URL: $url",
                    code = ErrorCode.EXTRACTION_FAILED
                )
            }

            val durationSeconds = streamInfo.duration
            val durationMs = if (durationSeconds > 0) durationSeconds * 1000L else 0L
            val thumbnail = streamInfo.thumbnails?.firstOrNull()?.url

            AppResult.Success(
                MediaProbeResult(
                    url = streamInfo.url ?: url,
                    title = streamInfo.name ?: "YouTube Media",
                    uploader = streamInfo.uploaderName,
                    durationMs = durationMs,
                    thumbnailUrl = thumbnail,
                    sourcePlatform = MediaSource.YOUTUBE,
                    availableFormats = formats,
                    isDrmProtected = false,
                    requiresAuthentication = false
                )
            )

        } catch (e: ContentNotAvailableException) {
            AppResult.Error(
                message = "PLATFORM_EXTRACTION_UNAVAILABLE: YouTube video is unavailable or deleted: ${e.message}",
                cause = e,
                code = ErrorCode.EXTRACTION_FAILED
            )
        } catch (e: ParsingException) {
            AppResult.Error(
                message = "PLATFORM_EXTRACTION_UNAVAILABLE: YouTube page parsing failed (upstream changes or challenge): ${e.message}",
                cause = e,
                code = ErrorCode.EXTRACTION_FAILED
            )
        } catch (e: ExtractionException) {
            AppResult.Error(
                message = "PLATFORM_EXTRACTION_UNAVAILABLE: YouTube extraction failed: ${e.message}",
                cause = e,
                code = ErrorCode.EXTRACTION_FAILED
            )
        } catch (e: Exception) {
            AppResult.Error(
                message = "PLATFORM_EXTRACTION_UNAVAILABLE: Unexpected YouTube extraction error: ${e.message}",
                cause = e,
                code = ErrorCode.EXTRACTION_FAILED
            )
        }
    }

    override suspend fun extractStream(url: String, requestedFormatId: String): AppResult<ResolvedPlatformStream> = withContext(Dispatchers.IO) {
        try {
            NewPipeRuntime.ensureInitialized(downloaderBridge)

            val targetUrl = normalizeYouTubeUrl(url)
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, targetUrl)

            // P2-YT-FINAL-02 / P2-TRUTH-LOCK-02: Exact format matching on stable upstream itags
            if (requestedFormatId.startsWith("youtube:audio:itag:")) {
                val targetItag = requestedFormatId.removePrefix("youtube:audio:itag:").toIntOrNull()
                if (targetItag != null && targetItag > 0) {
                    val matchingAudio = streamInfo.audioStreams?.find { it.itag == targetItag }
                    if (matchingAudio != null) {
                        val streamUrl = matchingAudio.url
                        if (!streamUrl.isNullOrBlank()) {
                            return@withContext AppResult.Success(
                                ResolvedPlatformStream(
                                    formatId = requestedFormatId,
                                    directStreamUrl = streamUrl,
                                    platform = MediaSource.YOUTUBE,
                                    isAudioOnly = true,
                                    itag = matchingAudio.itag,
                                    resolution = if (matchingAudio.averageBitrate > 0) "${matchingAudio.averageBitrate} kbps" else "Audio",
                                    mimeType = matchingAudio.format?.mimeType
                                )
                            )
                        }
                    }
                }
            }

            if (requestedFormatId.startsWith("youtube:video:itag:")) {
                val targetItag = requestedFormatId.removePrefix("youtube:video:itag:").toIntOrNull()
                if (targetItag != null && targetItag > 0) {
                    val matchingVideo = streamInfo.videoStreams?.find { it.itag == targetItag }
                    if (matchingVideo != null) {
                        val streamUrl = matchingVideo.url
                        if (!streamUrl.isNullOrBlank()) {
                            return@withContext AppResult.Success(
                                ResolvedPlatformStream(
                                    formatId = requestedFormatId,
                                    directStreamUrl = streamUrl,
                                    platform = MediaSource.YOUTUBE,
                                    isAudioOnly = false,
                                    itag = matchingVideo.itag,
                                    resolution = matchingVideo.resolution,
                                    mimeType = matchingVideo.format?.mimeType
                                )
                            )
                        }
                    }
                }
            }

            // P2-YT-FINAL-02 Invariant: ZERO fallbacks to firstAudio, firstVideo, or anyStream!
            AppResult.Error(
                message = "PLATFORM_EXTRACTION_UNAVAILABLE: Exact requested stream format '$requestedFormatId' is not available in YouTube stream",
                code = ErrorCode.EXTRACTION_FAILED
            )

        } catch (e: Exception) {
            AppResult.Error(
                message = "PLATFORM_EXTRACTION_UNAVAILABLE: Failed extracting direct stream: ${e.message}",
                cause = e,
                code = ErrorCode.EXTRACTION_FAILED
            )
        }
    }
}
