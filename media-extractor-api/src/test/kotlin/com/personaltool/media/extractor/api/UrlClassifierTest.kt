package com.personaltool.media.extractor.api

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.model.media.MediaSource
import org.junit.Test
import java.net.InetAddress

class UrlClassifierTest {

    private val publicDns = DnsLookup { listOf(InetAddress.getByName("93.184.216.34")) }

    @Test
    fun youtubeUrls_areCorrectlyClassified_withExtractedVideoId() {
        val urls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://youtube.com/shorts/abcdefghijk" to "abcdefghijk"
        )

        for ((url, expectedId) in urls) {
            val result = UrlClassifier.validateAndNormalize(url, publicDns)
            assertThat(result).isInstanceOf(UrlValidationResult.Valid::class.java)
            val valid = result as UrlValidationResult.Valid
            assertThat(valid.platform).isEqualTo(MediaSource.YOUTUBE)
            assertThat(valid.platformContentId).isEqualTo(expectedId)
        }
    }

    @Test
    fun instagramUrls_areCorrectlyClassified_withShortcode() {
        val urls = listOf(
            "https://www.instagram.com/reel/C1234567890/" to "C1234567890",
            "https://instagram.com/p/C9876543210" to "C9876543210"
        )

        for ((url, expectedId) in urls) {
            val result = UrlClassifier.validateAndNormalize(url, publicDns)
            assertThat(result).isInstanceOf(UrlValidationResult.Valid::class.java)
            val valid = result as UrlValidationResult.Valid
            assertThat(valid.platform).isEqualTo(MediaSource.INSTAGRAM)
            assertThat(valid.platformContentId).isEqualTo(expectedId)
        }
    }

    @Test
    fun xTwitterUrls_areCorrectlyClassified_withTweetId() {
        val urls = listOf(
            "https://x.com/username/status/1234567890123456789" to "1234567890123456789",
            "https://twitter.com/user/status/987654321" to "987654321",
            "https://mobile.twitter.com/user/status/987654321" to "987654321"
        )

        for ((url, expectedId) in urls) {
            val result = UrlClassifier.validateAndNormalize(url, publicDns)
            assertThat(result).isInstanceOf(UrlValidationResult.Valid::class.java)
            val valid = result as UrlValidationResult.Valid
            assertThat(valid.platform).isEqualTo(MediaSource.X_TWITTER)
            assertThat(valid.platformContentId).isEqualTo(expectedId)
        }
    }

    @Test
    fun spoofedPlatformSubdomains_areNotClassifiedAsPlatforms() {
        val attackerUrls = listOf(
            "https://youtube.com.attacker.example/watch?v=dQw4w9WgXcQ",
            "https://www.instagram.com.phishing.net/p/123456",
            "https://x.com.evilcorp.org/user/status/123456",
            "https://notyoutube.com/video.mp4"
        )

        for (url in attackerUrls) {
            val result = UrlClassifier.validateAndNormalize(url, publicDns)
            assertThat(result).isInstanceOf(UrlValidationResult.Valid::class.java)
            val valid = result as UrlValidationResult.Valid
            assertThat(valid.platform).isEqualTo(MediaSource.GENERIC_URL)
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
            val result = UrlClassifier.validateAndNormalize(url, publicDns)
            assertThat(result).isInstanceOf(UrlValidationResult.Invalid::class.java)
        }
    }

    @Test
    fun localHostAndPrivateIps_areProhibitedBySsrfPolicy() {
        val prohibited = listOf(
            "http://localhost/test",
            "http://127.0.0.1:8080/exploit",
            "https://192.168.1.1/admin",
            "http://10.0.0.1/resource",
            "http://172.16.0.1/internal",
            "http://169.254.169.254/latest/meta-data/"
        )

        for (url in prohibited) {
            val result = UrlClassifier.validateAndNormalize(url)
            assertThat(result).isInstanceOf(UrlValidationResult.Invalid::class.java)
            val invalid = result as UrlValidationResult.Invalid
            assertThat(invalid.isSsrfViolation).isTrue()
        }
    }
}
