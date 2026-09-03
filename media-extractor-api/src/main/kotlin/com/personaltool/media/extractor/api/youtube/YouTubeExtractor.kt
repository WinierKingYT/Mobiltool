package com.personaltool.media.extractor.api.youtube

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.media.MediaSource
import com.personaltool.media.extractor.api.MediaProbeResult

data class ResolvedPlatformStream(
    val formatId: String,
    val directStreamUrl: String,
    val platform: MediaSource = MediaSource.YOUTUBE,
    val isAudioOnly: Boolean,
    val itag: Int = 0,
    val resolution: String? = null,
    val mimeType: String? = null
)

interface YouTubeExtractor {
    suspend fun probeYouTubeUrl(url: String): AppResult<MediaProbeResult>
    suspend fun extractStream(url: String, requestedFormatId: String): AppResult<ResolvedPlatformStream>
}
