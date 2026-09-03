package com.personaltool.media.extractor.api.youtube

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import com.personaltool.core.model.media.MediaFormatOption
import com.personaltool.core.model.media.MediaSource
import com.personaltool.media.extractor.api.MediaProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.atomic.AtomicBoolean

class NewPipeYouTubeExtractor(
    private val downloaderBridge: NewPipeDownloaderBridge = NewPipeDownloaderBridge()
) : YouTubeExtractor {

    companion object {
        private val isInitialized = AtomicBoolean(false)

        private fun ensureInitialized(bridge: NewPipeDownloaderBridge) {
            if (isInitialized.compareAndSet(false, true)) {
                NewPipe.init(bridge)
            }
        }

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
            ensureInitialized(downloaderBridge)

            val targetUrl = normalizeYouTubeUrl(url)
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, targetUrl)

            val formats = mutableListOf<MediaFormatOption>()

            // 1. Video Streams (progressive / muxed or video-only)
            streamInfo.videoStreams?.forEachIndexed { idx, vStream ->
                val res = vStream.resolution ?: "Video"
                val fmtName = vStream.format?.name ?: "mp4"
                val ext = vStream.format?.suffix ?: "mp4"
                formats.add(
                    MediaFormatOption(
                        formatId = "yt-video-$idx-$res-$fmtName",
                        ext = ext,
                        resolution = res,
                        note = "YouTube Video ($res $fmtName)",
                        fileSizeBytes = null,
                        isAudioOnly = false,
                        videoCodec = vStream.codec,
                        audioCodec = null
                    )
                )
            }

            // 2. Audio Streams
            streamInfo.audioStreams?.forEachIndexed { idx, aStream ->
                val fmtName = aStream.format?.name ?: "m4a"
                val ext = aStream.format?.suffix ?: "m4a"
                val bitrate = aStream.averageBitrate
                val resLabel = if (bitrate > 0) "$bitrate kbps Audio" else "Audio Stream"
                formats.add(
                    MediaFormatOption(
                        formatId = "yt-audio-$idx-$fmtName",
                        ext = ext,
                        resolution = resLabel,
                        note = "YouTube Audio ($fmtName)",
                        fileSizeBytes = null,
                        isAudioOnly = true,
                        videoCodec = null,
                        audioCodec = aStream.codec
                    )
                )
            }

            if (formats.isEmpty()) {
                return@withContext AppResult.Error(
                    message = "PLATFORM_EXTRACTION_UNAVAILABLE: No playable public video or audio streams found for YouTube URL: $url",
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

    override suspend fun extractStreamUrl(url: String, formatId: String): AppResult<String> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized(downloaderBridge)

            val targetUrl = normalizeYouTubeUrl(url)
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, targetUrl)

            // Look up matching stream
            if (formatId.startsWith("yt-audio-")) {
                val audioIndex = formatId.removePrefix("yt-audio-").substringBefore('-').toIntOrNull()
                val aStreams = streamInfo.audioStreams
                if (audioIndex != null && aStreams != null && audioIndex in aStreams.indices) {
                    val streamUrl = aStreams[audioIndex].url
                    if (!streamUrl.isNullOrBlank()) {
                        return@withContext AppResult.Success(streamUrl)
                    }
                }
                val firstAudio = aStreams?.firstOrNull()?.url
                if (!firstAudio.isNullOrBlank()) {
                    return@withContext AppResult.Success(firstAudio)
                }
            } else if (formatId.startsWith("yt-video-")) {
                val videoIndex = formatId.removePrefix("yt-video-").substringBefore('-').toIntOrNull()
                val vStreams = streamInfo.videoStreams
                if (videoIndex != null && vStreams != null && videoIndex in vStreams.indices) {
                    val streamUrl = vStreams[videoIndex].url
                    if (!streamUrl.isNullOrBlank()) {
                        return@withContext AppResult.Success(streamUrl)
                    }
                }
                val firstVideo = vStreams?.firstOrNull()?.url
                if (!firstVideo.isNullOrBlank()) {
                    return@withContext AppResult.Success(firstVideo)
                }
            }

            val anyStream = streamInfo.videoStreams?.firstOrNull()?.url
                ?: streamInfo.audioStreams?.firstOrNull()?.url

            if (!anyStream.isNullOrBlank()) {
                AppResult.Success(anyStream)
            } else {
                AppResult.Error(
                    message = "PLATFORM_EXTRACTION_UNAVAILABLE: Could not resolve playable stream URL for format $formatId",
                    code = ErrorCode.EXTRACTION_FAILED
                )
            }

        } catch (e: Exception) {
            AppResult.Error(
                message = "PLATFORM_EXTRACTION_UNAVAILABLE: Failed extracting direct stream URL: ${e.message}",
                cause = e,
                code = ErrorCode.EXTRACTION_FAILED
            )
        }
    }
}
