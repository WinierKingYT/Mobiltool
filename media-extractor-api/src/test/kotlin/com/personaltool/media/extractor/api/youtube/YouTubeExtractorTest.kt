package com.personaltool.media.extractor.api.youtube

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import com.personaltool.core.model.media.MediaFormatOption
import com.personaltool.core.model.media.MediaSource
import com.personaltool.media.extractor.api.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.schabi.newpipe.extractor.downloader.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress

class YouTubeExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val publicDns = DnsLookup { listOf(InetAddress.getByName("93.184.216.34")) }

    private fun createValidMp4Payload(): ByteArray {
        val header = byteArrayOf(0x00, 0x00, 0x00, 0x20, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte())
        val body = ByteArray(4096) { (it % 250).toByte() }
        return header + body
    }

    private fun createFakeEngine(payload: ByteArray): SafeHttpTransportEngine = object : SafeHttpTransportEngine {
        override fun openSafeConnection(
            initialUrl: String,
            method: String,
            headers: Map<String, String>,
            maxRedirects: Int,
            connectTimeoutMs: Long,
            readTimeoutMs: Long,
            dnsLookup: DnsLookup
        ): AppResult<SafeHttpResponse> {
            return AppResult.Success(
                SafeHttpResponse(
                    response = null,
                    responseBodyStream = ByteArrayInputStream(payload),
                    contentLength = payload.size.toLong(),
                    contentType = "video/mp4",
                    requestedUrl = initialUrl,
                    finalUrl = initialUrl,
                    responseCode = 200,
                    redirectCount = 0
                )
            )
        }
    }

    @Test
    fun probeYouTubeUrl_withFakeExtractor_returnsTruthfulMediaProbeResult() = runTest {
        val fakeYt = object : YouTubeExtractor {
            override suspend fun probeYouTubeUrl(url: String): AppResult<MediaProbeResult> {
                return AppResult.Success(
                    MediaProbeResult(
                        url = url,
                        title = "Sample Video Title",
                        uploader = "Test Creator",
                        durationMs = 180000L,
                        thumbnailUrl = "https://img.youtube.com/vi/sample123/hqdefault.jpg",
                        sourcePlatform = MediaSource.YOUTUBE,
                        availableFormats = listOf(
                            MediaFormatOption(
                                formatId = "yt-video-0-720p-mp4",
                                ext = "mp4",
                                resolution = "720p",
                                note = "YouTube Video (720p mp4)",
                                isAudioOnly = false,
                                videoCodec = "avc1.4d401f"
                            ),
                            MediaFormatOption(
                                formatId = "yt-audio-0-m4a",
                                ext = "m4a",
                                resolution = "128 kbps Audio",
                                note = "YouTube Audio (m4a)",
                                isAudioOnly = true,
                                audioCodec = "mp4a.40.2"
                            )
                        )
                    )
                )
            }

            override suspend fun extractStreamUrl(url: String, formatId: String): AppResult<String> {
                return AppResult.Success("https://googlevideo.example.com/videoplayback?id=sample123")
            }
        }

        val defaultExtractor = DefaultMediaExtractor(
            dnsLookup = publicDns,
            youtubeExtractor = fakeYt
        )

        val probeResult = defaultExtractor.probeUrl("https://www.youtube.com/watch?v=sample123")
        assertThat(probeResult).isInstanceOf(AppResult.Success::class.java)
        val probe = (probeResult as AppResult.Success).data
        assertThat(probe.title).isEqualTo("Sample Video Title")
        assertThat(probe.uploader).isEqualTo("Test Creator")
        assertThat(probe.durationMs).isEqualTo(180000L)
        assertThat(probe.sourcePlatform).isEqualTo(MediaSource.YOUTUBE)
        assertThat(probe.availableFormats).hasSize(2)
        assertThat(probe.availableFormats[0].isAudioOnly).isFalse()
        assertThat(probe.availableFormats[1].isAudioOnly).isTrue()
    }

    @Test
    fun downloadYouTubeMedia_extractsDirectStreamUrl_andDownloadsThroughVerifiedPipeline() = runTest {
        val payload = createValidMp4Payload()
        val fakeEngine = createFakeEngine(payload)
        val streamDownloader = RealHttpStreamDownloader(dnsLookup = publicDns, transportEngine = fakeEngine)

        var streamUrlExtracted = false
        val fakeYt = object : YouTubeExtractor {
            override suspend fun probeYouTubeUrl(url: String): AppResult<MediaProbeResult> {
                return AppResult.Success(
                    MediaProbeResult(
                        url = url,
                        title = "Sample Video",
                        sourcePlatform = MediaSource.YOUTUBE,
                        availableFormats = listOf(
                            MediaFormatOption("yt-video-0-720p-mp4", "mp4", "720p")
                        )
                    )
                )
            }

            override suspend fun extractStreamUrl(url: String, formatId: String): AppResult<String> {
                streamUrlExtracted = true
                return AppResult.Success("https://googlevideo.example.com/videoplayback?id=123")
            }
        }

        val defaultExtractor = DefaultMediaExtractor(
            dnsLookup = publicDns,
            streamDownloader = streamDownloader,
            youtubeExtractor = fakeYt
        )

        val destFile = File(tempFolder.root, "downloaded_yt.mp4")
        val request = DownloadRequest(
            id = "dl-yt-01",
            sourceUrl = "https://www.youtube.com/watch?v=sample123",
            formatId = "yt-video-0-720p-mp4",
            destinationPath = destFile.absolutePath
        )

        val result = defaultExtractor.downloadMedia(request) {}
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val mediaResult = (result as AppResult.Success).data

        assertThat(streamUrlExtracted).isTrue()
        assertThat(destFile.exists()).isTrue()
        assertThat(mediaResult.fileSizeBytes).isEqualTo(payload.size.toLong())
        assertThat(destFile.length()).isEqualTo(payload.size.toLong())
    }

    @Test
    fun unavailableYouTubeVideo_failsClosedWithExtractionFailed() = runTest {
        val fakeYt = object : YouTubeExtractor {
            override suspend fun probeYouTubeUrl(url: String): AppResult<MediaProbeResult> {
                return AppResult.Error(
                    message = "PLATFORM_EXTRACTION_UNAVAILABLE: YouTube video is unavailable or deleted",
                    code = ErrorCode.EXTRACTION_FAILED
                )
            }

            override suspend fun extractStreamUrl(url: String, formatId: String): AppResult<String> {
                return AppResult.Error("Video unavailable", code = ErrorCode.EXTRACTION_FAILED)
            }
        }

        val defaultExtractor = DefaultMediaExtractor(dnsLookup = publicDns, youtubeExtractor = fakeYt)
        val result = defaultExtractor.probeUrl("https://www.youtube.com/watch?v=deleted_video")

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.EXTRACTION_FAILED)
        assertThat(error.message).contains("PLATFORM_EXTRACTION_UNAVAILABLE")
    }

    @Test
    fun instagramAndX_remainFailingClosedWithExtractionUnavailable() = runTest {
        val defaultExtractor = DefaultMediaExtractor(dnsLookup = publicDns)

        val igResult = defaultExtractor.probeUrl("https://www.instagram.com/reel/C1234567890/")
        assertThat(igResult).isInstanceOf(AppResult.Error::class.java)
        val igError = igResult as AppResult.Error
        assertThat(igError.code).isEqualTo(ErrorCode.EXTRACTION_FAILED)
        assertThat(igError.message).contains("PLATFORM_EXTRACTION_UNAVAILABLE")

        val xResult = defaultExtractor.probeUrl("https://x.com/tech_user/status/987654321")
        assertThat(xResult).isInstanceOf(AppResult.Error::class.java)
        val xError = xResult as AppResult.Error
        assertThat(xError.code).isEqualTo(ErrorCode.EXTRACTION_FAILED)
        assertThat(xError.message).contains("PLATFORM_EXTRACTION_UNAVAILABLE")
    }

    // ==========================================
    // P2-YT-FINAL-01: NewPipeDownloaderBridge Security Tests
    // ==========================================

    @Test
    fun bridge_rejectsPrivateIpDestination_throwsIOException() {
        val bridge = NewPipeDownloaderBridge(dnsLookup = publicDns)
        val privateUrls = listOf(
            "http://192.168.1.1/test",
            "http://10.0.0.1/test",
            "http://127.0.0.1/test",
            "http://169.254.169.254/latest/meta-data",
            "http://localhost:8080/internal"
        )

        for (url in privateUrls) {
            val req = Request.newBuilder()
                .url(url)
                .httpMethod("GET")
                .headers(emptyMap())
                .build()
            try {
                bridge.execute(req)
                fail("Expected IOException for private URL: $url")
            } catch (e: IOException) {
                assertThat(e.message).contains("Network policy blocked request")
            }
        }
    }

    @Test
    fun bridge_rejectsCredentialsInUrl_throwsIOException() {
        val bridge = NewPipeDownloaderBridge(dnsLookup = publicDns)
        val req = Request.newBuilder()
            .url("https://admin:secret@example.com/api")
            .httpMethod("GET")
            .headers(emptyMap())
            .build()

        try {
            bridge.execute(req)
            fail("Expected IOException for URL with credentials")
        } catch (e: IOException) {
            assertThat(e.message).contains("Network policy blocked request")
        }
    }

    @Test
    fun bridge_rejectsNonHttpSchemes_throwsIOException() {
        val bridge = NewPipeDownloaderBridge(dnsLookup = publicDns)
        val invalidSchemes = listOf("file:///etc/passwd", "ftp://example.com/file", "javascript:alert(1)")

        for (url in invalidSchemes) {
            val req = Request.newBuilder()
                .url(url)
                .httpMethod("GET")
                .headers(emptyMap())
                .build()
            try {
                bridge.execute(req)
                fail("Expected IOException for invalid scheme URL: $url")
            } catch (e: IOException) {
                assertThat(e.message).contains("Network policy blocked request")
            }
        }
    }
}
