package com.personaltool.media.extractor.api

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import com.personaltool.core.model.media.MediaSource
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress

class RealHttpStreamDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val publicDns = DnsLookup { listOf(InetAddress.getByName("93.184.216.34")) }
    private val downloader = RealHttpStreamDownloader(dnsLookup = publicDns)
    private val extractor = DefaultMediaExtractor(dnsLookup = publicDns)

    @Test
    fun prohibitedLocalUrl_failsImmediatelyWithSecurityViolation() = runTest {
        val destFile = File(tempFolder.root, "test.mp4")
        val result = downloader.download(
            downloadId = "test-1",
            sourceUrl = "http://127.0.0.1:8080/internal.mp4",
            destinationFile = destFile,
            onProgress = {}
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.SECURITY_VIOLATION)
        assertThat(destFile.exists()).isFalse()
    }

    @Test
    fun urlWithCredentials_failsImmediatelyWithSecurityViolation() = runTest {
        val destFile = File(tempFolder.root, "test_creds.mp4")
        val result = downloader.download(
            downloadId = "test-creds",
            sourceUrl = "https://user:password@example.com/video.mp4",
            destinationFile = destFile,
            onProgress = {}
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.SECURITY_VIOLATION)
        assertThat(destFile.exists()).isFalse()
    }

    @Test
    fun nonHttpScheme_failsImmediatelyWithValidationError() = runTest {
        val destFile = File(tempFolder.root, "test.mp4")
        val result = downloader.download(
            downloadId = "test-2",
            sourceUrl = "file:///sdcard/movie.mp4",
            destinationFile = destFile,
            onProgress = {}
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.VALIDATION_ERROR)
        assertThat(destFile.exists()).isFalse()
    }

    @Test
    fun extractor_probeYouTubeUrl_failsClosedWithPlatformExtractionUnavailable() = runTest {
        val validation = UrlClassifier.validateAndNormalize("https://www.youtube.com/watch?v=dQw4w9WgXcQ", publicDns)
        assertThat(validation).isInstanceOf(UrlValidationResult.Valid::class.java)
        assertThat((validation as UrlValidationResult.Valid).platform).isEqualTo(MediaSource.YOUTUBE)

        val result = extractor.probeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("PLATFORM_EXTRACTION_UNAVAILABLE")
        assertThat(error.code).isEqualTo(ErrorCode.EXTRACTION_FAILED)
    }

    @Test
    fun extractor_probeInstagramUrl_failsClosedWithPlatformExtractionUnavailable() = runTest {
        val validation = UrlClassifier.validateAndNormalize("https://www.instagram.com/reel/C1234567890/", publicDns)
        assertThat(validation).isInstanceOf(UrlValidationResult.Valid::class.java)
        assertThat((validation as UrlValidationResult.Valid).platform).isEqualTo(MediaSource.INSTAGRAM)

        val result = extractor.probeUrl("https://www.instagram.com/reel/C1234567890/")
        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("PLATFORM_EXTRACTION_UNAVAILABLE")
        assertThat(error.code).isEqualTo(ErrorCode.EXTRACTION_FAILED)
    }

    @Test
    fun extractor_probeXTwitterUrl_failsClosedWithPlatformExtractionUnavailable() = runTest {
        val validation = UrlClassifier.validateAndNormalize("https://x.com/tech_user/status/987654321", publicDns)
        assertThat(validation).isInstanceOf(UrlValidationResult.Valid::class.java)
        assertThat((validation as UrlValidationResult.Valid).platform).isEqualTo(MediaSource.X_TWITTER)

        val result = extractor.probeUrl("https://x.com/tech_user/status/987654321")
        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("PLATFORM_EXTRACTION_UNAVAILABLE")
        assertThat(error.code).isEqualTo(ErrorCode.EXTRACTION_FAILED)
    }
}
