package com.personaltool.media.extractor.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HttpMediaProberTest {

    @Test
    fun extractFileName_withRfc5987ContentDisposition_decodesUtf8Correctly() {
        val cd = "attachment; filename*=UTF-8''video%20test%20%C3%A7%C4%B1nar.mp4"
        val fileName = HttpMediaProber.extractFileName("https://example.com/stream", cd, "video/mp4")
        assertThat(fileName).contains(".mp4")
    }

    @Test
    fun extractFileName_withStandardContentDisposition_sanitizesCharacters() {
        val cd = """attachment; filename="evil/path:test*file?.mp4""""
        val fileName = HttpMediaProber.extractFileName("https://example.com/stream", cd, "video/mp4")
        assertThat(fileName).doesNotContain("/")
        assertThat(fileName).doesNotContain(":")
        assertThat(fileName).doesNotContain("*")
        assertThat(fileName).doesNotContain("?")
    }

    @Test
    fun extractFileName_withUrlPath_derivesFromLastSegment() {
        val url = "https://cdn.example.com/videos/2026/presentation_hd.webm"
        val fileName = HttpMediaProber.extractFileName(url, null, "video/webm")
        assertThat(fileName).isEqualTo("presentation_hd.webm")
    }

    @Test
    fun extractFileName_fallbackUsesTimestampAndMimeExtension() {
        val url = "https://cdn.example.com/stream"
        val fileName = HttpMediaProber.extractFileName(url, null, "audio/mpeg")
        assertThat(fileName).startsWith("media_download_")
        assertThat(fileName).endsWith(".mp3")
    }
}
