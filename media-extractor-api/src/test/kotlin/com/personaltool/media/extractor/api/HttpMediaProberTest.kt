package com.personaltool.media.extractor.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HttpMediaProberTest {

    @Test
    fun extractFileName_fromContentDisposition_returnsCleanFilename() {
        val cd = "attachment; filename=\"custom_song_track.mp3\""
        val fileName = HttpMediaProber.extractFileName("https://example.com/download?id=123", cd, "audio/mpeg")
        assertThat(fileName).isEqualTo("custom_song_track.mp3")
    }

    @Test
    fun extractFileName_fromUrlPath_returnsPathFilename() {
        val fileName = HttpMediaProber.extractFileName("https://example.com/assets/media/demo_video.mp4", null, "video/mp4")
        assertThat(fileName).isEqualTo("demo_video.mp4")
    }

    @Test
    fun extractFileName_fromFallbackMimeType_generatesValidExtension() {
        val fileName = HttpMediaProber.extractFileName("https://example.com/stream/v1", null, "video/mp4")
        assertThat(fileName).startsWith("media_download_")
        assertThat(fileName).endsWith(".mp4")
    }
}
