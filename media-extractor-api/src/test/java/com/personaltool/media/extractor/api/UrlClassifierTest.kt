package com.personaltool.media.extractor.api

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.model.media.MediaSource
import org.junit.Test

class UrlClassifierTest {

    @Test
    fun youtubeUrls_areCorrectlyClassified() {
        val urls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://youtube.com/shorts/abcdefghijk"
        )

        for (url in urls) {
            val result = UrlClassifier.validateAndNormalize(url)
            assertThat(result).isInstanceOf(UrlValidationResult.Valid::class.java)
            val valid = result as UrlValidationResult.Valid
            assertThat(valid.platform).isEqualTo(MediaSource.YOUTUBE)
        }
    }

    @Test
    fun instagramUrls_areCorrectlyClassified() {
        val urls = listOf(
            "https://www.instagram.com/reel/C1234567890/",
            "https://instagram.com/p/C9876543210"
        )

        for (url in urls) {
            val result = UrlClassifier.validateAndNormalize(url)
            assertThat(result).isInstanceOf(UrlValidationResult.Valid::class.java)
            val valid = result as UrlValidationResult.Valid
            assertThat(valid.platform).isEqualTo(MediaSource.INSTAGRAM)
        }
    }

    @Test
    fun xTwitterUrls_areCorrectlyClassified() {
        val urls = listOf(
            "https://x.com/username/status/1234567890123456789",
            "https://twitter.com/user/status/987654321"
        )

        for (url in urls) {
            val result = UrlClassifier.validateAndNormalize(url)
            assertThat(result).isInstanceOf(UrlValidationResult.Valid::class.java)
            val valid = result as UrlValidationResult.Valid
            assertThat(valid.platform).isEqualTo(MediaSource.X_TWITTER)
        }
    }

    @Test
    fun nonHttpSchemes_areRejected() {
        val invalidSchemes = listOf(
            "file:///sdcard/video.mp4",
            "content://media/external/video/1",
            "javascript:alert(1)",
            "ftp://files.example.com/audio.mp3"
        )

        for (url in invalidSchemes) {
            val result = UrlClassifier.validateAndNormalize(url)
            assertThat(result).isInstanceOf(UrlValidationResult.Invalid::class.java)
        }
    }

    @Test
    fun localhostAndPrivateIPs_areProhibitedByInvariant() {
        val prohibited = listOf(
            "http://localhost/test",
            "http://127.0.0.1:8080/exploit",
            "https://192.168.1.1/admin",
            "http://10.0.0.1/resource"
        )

        for (url in prohibited) {
            val result = UrlClassifier.validateAndNormalize(url)
            assertThat(result).isInstanceOf(UrlValidationResult.Invalid::class.java)
        }
    }
}
