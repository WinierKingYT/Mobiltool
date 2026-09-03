package com.personaltool.media.extractor.api.youtube

import com.personaltool.core.common.result.AppResult
import com.personaltool.media.extractor.api.MediaProbeResult

interface YouTubeExtractor {
    suspend fun probeYouTubeUrl(url: String): AppResult<MediaProbeResult>
    suspend fun extractStreamUrl(url: String, formatId: String): AppResult<String>
}
