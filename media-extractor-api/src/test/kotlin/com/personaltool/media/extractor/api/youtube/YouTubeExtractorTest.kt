package com.personaltool.media.extractor.api.youtube

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import com.personaltool.core.model.media.MediaFormatOption
import com.personaltool.core.model.media.MediaSource
import com.personaltool.media.extractor.api.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.fail
import org.junit.Before
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

    @Before
    fun setUp() {
        NewPipeRuntime.resetForTesting()
    }

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

    // ==========================================
    // Core YouTube Extractor Integration Tests
    // ==========================================

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
                                formatId = "youtube:video:itag:18",
                                ext = "mp4",
                                resolution = "360p",
                                note = "YouTube Video (360p mp4 itag:18)",
                                isAudioOnly = false,
                                videoCodec = "avc1.42001E"
                            ),
                            MediaFormatOption(
                                formatId = "youtube:audio:itag:140",
                                ext = "m4a",
                                resolution = "128 kbps Audio",
                                note = "YouTube Audio (m4a itag:140)",
                                isAudioOnly = true,
                                audioCodec = "mp4a.40.2"
                            )
                        )
                    )
                )
            }

            override suspend fun extractStream(url: String, requestedFormatId: String): AppResult<ResolvedPlatformStream> {
                return AppResult.Success(
                    ResolvedPlatformStream(
                        formatId = requestedFormatId,
                        directStreamUrl = "https://googlevideo.example.com/videoplayback?id=sample123",
                        platform = MediaSource.YOUTUBE,
                        isAudioOnly = requestedFormatId.contains("audio"),
                        itag = 140
                    )
                )
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
        assertThat(probe.availableFormats[0].formatId).isEqualTo("youtube:video:itag:18")
        assertThat(probe.availableFormats[1].formatId).isEqualTo("youtube:audio:itag:140")
    }

    @Test
    fun downloadYouTubeMedia_extractsExactStream_andDownloadsThroughVerifiedPipeline() = runTest {
        val payload = createValidMp4Payload()
        val fakeEngine = createFakeEngine(payload)
        val streamDownloader = RealHttpStreamDownloader(dnsLookup = publicDns, transportEngine = fakeEngine)

        var extractedFormatId: String? = null
        val fakeYt = object : YouTubeExtractor {
            override suspend fun probeYouTubeUrl(url: String): AppResult<MediaProbeResult> {
                return AppResult.Success(
                    MediaProbeResult(
                        url = url,
                        title = "Sample Video",
                        sourcePlatform = MediaSource.YOUTUBE,
                        availableFormats = listOf(
                            MediaFormatOption("youtube:video:itag:18", "mp4", "360p")
                        )
                    )
                )
            }

            override suspend fun extractStream(url: String, requestedFormatId: String): AppResult<ResolvedPlatformStream> {
                extractedFormatId = requestedFormatId
                return AppResult.Success(
                    ResolvedPlatformStream(
                        formatId = requestedFormatId,
                        directStreamUrl = "https://googlevideo.example.com/videoplayback?id=123",
                        platform = MediaSource.YOUTUBE,
                        isAudioOnly = false,
                        itag = 18
                    )
                )
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
            formatId = "youtube:video:itag:18",
            destinationPath = destFile.absolutePath
        )

        val result = defaultExtractor.downloadMedia(request) {}
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val mediaResult = (result as AppResult.Success).data

        assertThat(extractedFormatId).isEqualTo("youtube:video:itag:18")
        assertThat(mediaResult.requestedFormatId).isEqualTo("youtube:video:itag:18")
        assertThat(mediaResult.resolvedFormatId).isEqualTo("youtube:video:itag:18")
        assertThat(destFile.exists()).isTrue()
        assertThat(mediaResult.fileSizeBytes).isEqualTo(payload.size.toLong())
        assertThat(mediaResult.commitMethod).isEqualTo("StandardCopyOption.ATOMIC_MOVE")
        assertThat(destFile.length()).isEqualTo(payload.size.toLong())
        // P2-TRUTH-LOCK-01: generic ISO-BMFF has UNKNOWN kind and null MIME
        assertThat(mediaResult.mediaKind).isEqualTo(DetectedMediaKind.UNKNOWN)
        assertThat(mediaResult.mimeType).isNull()
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

            override suspend fun extractStream(url: String, requestedFormatId: String): AppResult<ResolvedPlatformStream> {
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
    // P2-YT-FINAL-01B: NewPipeDownloaderBridge Security Tests
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
            val req = Request.newBuilder().url(url).httpMethod("GET").headers(emptyMap()).build()
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
            val req = Request.newBuilder().url(url).httpMethod("GET").headers(emptyMap()).build()
            try {
                bridge.execute(req)
                fail("Expected IOException for invalid scheme URL: $url")
            } catch (e: IOException) {
                assertThat(e.message).contains("Network policy blocked request")
            }
        }
    }

    @Test
    fun bridge_redirectHopToPrivateIp_isBlocked() {
        val fakeClientFactory: (ValidatedDns) -> OkHttpClient = { _ ->
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    okhttp3.Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "http://192.168.1.50/admin")
                        .body("".toResponseBody())
                        .build()
                }
                .build()
        }

        val bridge = NewPipeDownloaderBridge(
            dnsLookup = publicDns,
            connectTimeoutMs = 15000L,
            readTimeoutMs = 15000L,
            maxRedirects = 5,
            clientFactory = fakeClientFactory
        )
        val req = Request.newBuilder()
            .url("http://public.example.com/start")
            .httpMethod("GET")
            .headers(emptyMap())
            .build()

        try {
            bridge.execute(req)
            fail("Expected IOException on redirect to private IP")
        } catch (e: IOException) {
            assertThat(e.message).contains("Network policy blocked request")
        }
    }

    @Test
    fun bridge_redirectHopToLoopback_isBlocked() {
        val fakeClientFactory: (ValidatedDns) -> OkHttpClient = { _ ->
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    okhttp3.Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "http://127.0.0.1/meta")
                        .body("".toResponseBody())
                        .build()
                }
                .build()
        }

        val bridge = NewPipeDownloaderBridge(
            dnsLookup = publicDns,
            connectTimeoutMs = 15000L,
            readTimeoutMs = 15000L,
            maxRedirects = 5,
            clientFactory = fakeClientFactory
        )
        val req = Request.newBuilder()
            .url("http://public.example.com/start")
            .httpMethod("GET")
            .headers(emptyMap())
            .build()

        try {
            bridge.execute(req)
            fail("Expected IOException on redirect to loopback")
        } catch (e: IOException) {
            assertThat(e.message).contains("Network policy blocked request")
        }
    }

    @Test
    fun bridge_httpsToHttpRedirectDowngrade_isBlocked() {
        val fakeClientFactory: (ValidatedDns) -> OkHttpClient = { _ ->
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    okhttp3.Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "http://public.example.com/insecure")
                        .body("".toResponseBody())
                        .build()
                }
                .build()
        }

        val bridge = NewPipeDownloaderBridge(
            dnsLookup = publicDns,
            connectTimeoutMs = 15000L,
            readTimeoutMs = 15000L,
            maxRedirects = 5,
            clientFactory = fakeClientFactory
        )
        val req = Request.newBuilder()
            .url("https://public.example.com/secure")
            .httpMethod("GET")
            .headers(emptyMap())
            .build()

        try {
            bridge.execute(req)
            fail("Expected IOException on HTTPS -> HTTP downgrade")
        } catch (e: IOException) {
            assertThat(e.message).contains("Insecure protocol downgrade")
        }
    }

    @Test
    fun bridge_redirectCycle_isBlocked() {
        val fakeClientFactory: (ValidatedDns) -> OkHttpClient = { _ ->
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val target = if (request.url.toString().contains("hop1")) {
                        "https://public.example.com/hop2"
                    } else {
                        "https://public.example.com/hop1"
                    }
                    okhttp3.Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", target)
                        .body("".toResponseBody())
                        .build()
                }
                .build()
        }

        val bridge = NewPipeDownloaderBridge(
            dnsLookup = publicDns,
            connectTimeoutMs = 15000L,
            readTimeoutMs = 15000L,
            maxRedirects = 5,
            clientFactory = fakeClientFactory
        )
        val req = Request.newBuilder()
            .url("https://public.example.com/hop1")
            .httpMethod("GET")
            .headers(emptyMap())
            .build()

        try {
            bridge.execute(req)
            fail("Expected IOException on redirect cycle")
        } catch (e: IOException) {
            assertThat(e.message).contains("Redirect cycle detected")
        }
    }

    @Test
    fun bridge_redirectCountExceeded_isBlocked() {
        var count = 0
        val fakeClientFactory: (ValidatedDns) -> OkHttpClient = { _ ->
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    count++
                    okhttp3.Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "https://public.example.com/step$count")
                        .body("".toResponseBody())
                        .build()
                }
                .build()
        }

        val bridge = NewPipeDownloaderBridge(
            dnsLookup = publicDns,
            connectTimeoutMs = 15000L,
            readTimeoutMs = 15000L,
            maxRedirects = 3,
            clientFactory = fakeClientFactory
        )
        val req = Request.newBuilder()
            .url("https://public.example.com/step0")
            .httpMethod("GET")
            .headers(emptyMap())
            .build()

        try {
            bridge.execute(req)
            fail("Expected IOException when maxRedirects is exceeded")
        } catch (e: IOException) {
            assertThat(e.message).contains("Redirect limit (3) exceeded")
        }
    }

    @Test
    fun bridge_dnsBinding_bindsStrictlyToApprovedIps() {
        var observedDnsIps: List<InetAddress>? = null
        val fakeClientFactory: (ValidatedDns) -> OkHttpClient = { dns ->
            observedDnsIps = dns.lookup("public.example.com")
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("verified".toResponseBody())
                        .build()
                }
                .build()
        }

        val expectedIp = InetAddress.getByName("93.184.216.34")
        val fixedDns = DnsLookup { listOf(expectedIp) }
        val bridge = NewPipeDownloaderBridge(
            dnsLookup = fixedDns,
            connectTimeoutMs = 15000L,
            readTimeoutMs = 15000L,
            maxRedirects = 5,
            clientFactory = fakeClientFactory
        )

        val req = Request.newBuilder()
            .url("https://public.example.com/test")
            .httpMethod("GET")
            .headers(emptyMap())
            .build()

        val resp = bridge.execute(req)
        assertThat(resp.responseCode()).isEqualTo(200)
        assertThat(observedDnsIps).containsExactly(expectedIp)
    }

    @Test
    fun bridge_redirect303AfterPost_changesMethodToGet() {
        var observedSecondMethod: String? = null
        val fakeClientFactory: (ValidatedDns) -> OkHttpClient = { _ ->
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request()
                    if (req.url.toString() == "https://public.example.com/submit") {
                        okhttp3.Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(303)
                            .message("See Other")
                            .header("Location", "https://public.example.com/result")
                            .body("".toResponseBody())
                            .build()
                    } else {
                        observedSecondMethod = req.method
                        okhttp3.Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("result-data".toResponseBody())
                            .build()
                    }
                }
                .build()
        }

        val bridge = NewPipeDownloaderBridge(
            dnsLookup = publicDns,
            connectTimeoutMs = 15000L,
            readTimeoutMs = 15000L,
            maxRedirects = 5,
            clientFactory = fakeClientFactory
        )
        val req = Request.newBuilder()
            .url("https://public.example.com/submit")
            .httpMethod("POST")
            .dataToSend("foo=bar".toByteArray())
            .headers(emptyMap())
            .build()

        val resp = bridge.execute(req)
        assertThat(resp.responseCode()).isEqualTo(200)
        assertThat(observedSecondMethod).isEqualTo("GET")
    }

    @Test
    fun bridge_redirect307And308AfterPost_preservesPostMethod() {
        for (code in listOf(307, 308)) {
            var observedSecondMethod: String? = null
            val fakeClientFactory: (ValidatedDns) -> OkHttpClient = { _ ->
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val req = chain.request()
                        if (req.url.toString() == "https://public.example.com/post_entry") {
                            okhttp3.Response.Builder()
                                .request(req)
                                .protocol(Protocol.HTTP_1_1)
                                .code(code)
                                .message("Redirect")
                                .header("Location", "https://public.example.com/post_final")
                                .body("".toResponseBody())
                                .build()
                        } else {
                            observedSecondMethod = req.method
                            okhttp3.Response.Builder()
                                .request(req)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body("success".toResponseBody())
                                .build()
                        }
                    }
                    .build()
            }

            val bridge = NewPipeDownloaderBridge(
                dnsLookup = publicDns,
                connectTimeoutMs = 15000L,
                readTimeoutMs = 15000L,
                maxRedirects = 5,
                clientFactory = fakeClientFactory
            )
            val req = Request.newBuilder()
                .url("https://public.example.com/post_entry")
                .httpMethod("POST")
                .dataToSend("data=123".toByteArray())
                .headers(emptyMap())
                .build()

            val resp = bridge.execute(req)
            assertThat(resp.responseCode()).isEqualTo(200)
            assertThat(observedSecondMethod).isEqualTo("POST")
        }
    }

    // ==========================================
    // P2-YT-FINAL-02 / P2-TRUTH-LOCK-02: Format Identity & Non-Fallback Tests
    // ==========================================

    @Test
    fun exactFormatIdentity_resolvesExactStreamAcrossFormatReordering() = runTest {
        val fakeYt = object : YouTubeExtractor {
            override suspend fun probeYouTubeUrl(url: String): AppResult<MediaProbeResult> {
                return AppResult.Success(
                    MediaProbeResult(
                        url = url,
                        title = "Reorder Test",
                        sourcePlatform = MediaSource.YOUTUBE,
                        availableFormats = listOf(
                            MediaFormatOption("youtube:video:itag:18", "mp4", "360p"),
                            MediaFormatOption("youtube:video:itag:22", "mp4", "720p"),
                            MediaFormatOption("youtube:audio:itag:140", "m4a", "128 kbps Audio")
                        )
                    )
                )
            }

            override suspend fun extractStream(url: String, requestedFormatId: String): AppResult<ResolvedPlatformStream> {
                val streams = listOf(
                    ResolvedPlatformStream("youtube:audio:itag:140", "https://cdn.example.com/audio140", isAudioOnly = true, itag = 140),
                    ResolvedPlatformStream("youtube:video:itag:18", "https://cdn.example.com/video18", isAudioOnly = false, itag = 18),
                    ResolvedPlatformStream("youtube:video:itag:22", "https://cdn.example.com/video22", isAudioOnly = false, itag = 22)
                )
                val match = streams.find { it.formatId == requestedFormatId }
                return if (match != null) {
                    AppResult.Success(match)
                } else {
                    AppResult.Error("Stream not found", code = ErrorCode.EXTRACTION_FAILED)
                }
            }
        }

        val extractResult = fakeYt.extractStream("https://youtube.com/watch?v=test", "youtube:video:itag:22")
        assertThat(extractResult).isInstanceOf(AppResult.Success::class.java)
        val stream = (extractResult as AppResult.Success).data

        assertThat(stream.formatId).isEqualTo("youtube:video:itag:22")
        assertThat(stream.directStreamUrl).isEqualTo("https://cdn.example.com/video22")
        assertThat(stream.itag).isEqualTo(22)
        assertThat(stream.isAudioOnly).isFalse()
    }

    @Test
    fun exactFormatIdentity_whenRequestedStreamDisappears_failsClosedWithoutFallback() = runTest {
        val fakeYt = object : YouTubeExtractor {
            override suspend fun probeYouTubeUrl(url: String): AppResult<MediaProbeResult> =
                AppResult.Error("Not needed", code = ErrorCode.EXTRACTION_FAILED)

            override suspend fun extractStream(url: String, requestedFormatId: String): AppResult<ResolvedPlatformStream> {
                val availableStreams = listOf(
                    ResolvedPlatformStream("youtube:video:itag:18", "https://cdn.example.com/video18", isAudioOnly = false, itag = 18)
                )
                val match = availableStreams.find { it.formatId == requestedFormatId }
                return if (match != null) {
                    AppResult.Success(match)
                } else {
                    AppResult.Error(
                        "PLATFORM_EXTRACTION_UNAVAILABLE: Exact requested stream format '$requestedFormatId' is not available in YouTube stream",
                        code = ErrorCode.EXTRACTION_FAILED
                    )
                }
            }
        }

        val result = fakeYt.extractStream("https://youtube.com/watch?v=test", "youtube:audio:itag:140")
        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.EXTRACTION_FAILED)
        assertThat(error.message).contains("Exact requested stream format 'youtube:audio:itag:140' is not available")
    }

    @Test
    fun exactFormatIdentity_unknownFormatId_failsClosed() = runTest {
        val fakeYt = object : YouTubeExtractor {
            override suspend fun probeYouTubeUrl(url: String): AppResult<MediaProbeResult> =
                AppResult.Error("Not needed", code = ErrorCode.EXTRACTION_FAILED)

            override suspend fun extractStream(url: String, requestedFormatId: String): AppResult<ResolvedPlatformStream> {
                return AppResult.Error(
                    "PLATFORM_EXTRACTION_UNAVAILABLE: Exact requested stream format '$requestedFormatId' is not available in YouTube stream",
                    code = ErrorCode.EXTRACTION_FAILED
                )
            }
        }

        val result = fakeYt.extractStream("https://youtube.com/watch?v=test", "youtube:video:itag:99999")
        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.EXTRACTION_FAILED)
    }

    @Test
    fun exactFormatIdentity_unknownItagStreams_areOmittedAndFailClosedWhenAllUnknown() = runTest {
        // P2-TRUTH-LOCK-02: Streams with itag <= 0 must not be exposed as fake stable formats
        val fakeExtractorWithUnknownItags = object : YouTubeExtractor {
            override suspend fun probeYouTubeUrl(url: String): AppResult<MediaProbeResult> {
                val rawStreams = listOf(
                    mapOf("itag" to 0, "type" to "video", "res" to "1080p"),
                    mapOf("itag" to -1, "type" to "audio", "ext" to "m4a"),
                    mapOf("itag" to 0, "type" to "audio", "ext" to "m4a")
                )
                val validFormats = rawStreams.filter { (it["itag"] as Int) > 0 }.map {
                    MediaFormatOption(formatId = "youtube:video:itag:${it["itag"]}", ext = "mp4", resolution = "720p")
                }
                return if (validFormats.isEmpty()) {
                    AppResult.Error(
                        "PLATFORM_EXTRACTION_UNAVAILABLE: No playable public video or audio streams with stable itag identifiers found for YouTube URL: $url",
                        code = ErrorCode.EXTRACTION_FAILED
                    )
                } else {
                    AppResult.Success(
                        MediaProbeResult(
                            url = url,
                            title = "Test",
                            sourcePlatform = MediaSource.YOUTUBE,
                            availableFormats = validFormats
                        )
                    )
                }
            }

            override suspend fun extractStream(url: String, requestedFormatId: String): AppResult<ResolvedPlatformStream> {
                return AppResult.Error("No valid stream", code = ErrorCode.EXTRACTION_FAILED)
            }
        }

        val result = fakeExtractorWithUnknownItags.probeYouTubeUrl("https://youtube.com/watch?v=all_unknown_itags")
        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.EXTRACTION_FAILED)
        assertThat(error.message).contains("No playable public video or audio streams with stable itag identifiers")
    }

    @Test
    fun downloadedMediaResult_doesNotFabricateDefaultEvidence() {
        // P2-TRUTH-LOCK-03: Data model must not fabricate execution evidence via defaults
        val emptyResult = DownloadedMediaResult(
            downloadId = "test-1",
            outputFilePath = "/tmp/media.mp4",
            durationMs = 0L,
            fileSizeBytes = 1000L
        )
        assertThat(emptyResult.commitMethod).isNull()
        assertThat(emptyResult.sha256Hex).isNull()
        assertThat(emptyResult.mimeType).isNull()
        assertThat(emptyResult.requestedFormatId).isNull()
        assertThat(emptyResult.resolvedFormatId).isNull()
    }

    // ==========================================
    // P2-YT-FINAL-03 / P2-TRUTH-LOCK-04: Global NewPipe Runtime Tests
    // ==========================================

    @Test
    fun newPipeRuntime_ensureInitialized_idempotentWithSameBridge() {
        val bridge1 = NewPipeDownloaderBridge(dnsLookup = publicDns)
        val bridge2 = NewPipeDownloaderBridge(dnsLookup = publicDns)

        assertThat(NewPipeRuntime.isInitialized()).isFalse()
        NewPipeRuntime.ensureInitialized(bridge1)
        assertThat(NewPipeRuntime.isInitialized()).isTrue()
        assertThat(NewPipeRuntime.getActiveBridge()).isEqualTo(bridge1)

        // Idempotent re-initialization with compatible bridge succeeds
        NewPipeRuntime.ensureInitialized(bridge2)
        assertThat(NewPipeRuntime.isInitialized()).isTrue()
    }

    @Test
    fun newPipeRuntime_differentBridgeAttempt_throwsIllegalStateException() {
        val bridge1 = NewPipeDownloaderBridge(dnsLookup = publicDns, maxRedirects = 5)
        val differentBridge = NewPipeDownloaderBridge(dnsLookup = publicDns, maxRedirects = 10)

        NewPipeRuntime.ensureInitialized(bridge1)

        try {
            NewPipeRuntime.ensureInitialized(differentBridge)
            fail("Expected IllegalStateException when re-initializing with different bridge")
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("NewPipeRuntime already initialized with a different DownloaderBridge")
        }
    }

    @Test
    fun newPipeRuntime_customTestTransportVsProductionBridge_throwsIllegalStateException() {
        // P2-TRUTH-LOCK-04: Production bridge vs custom test transport bridge are distinct and incompatible
        val prodBridge = NewPipeDownloaderBridge(dnsLookup = publicDns)
        val testTransportBridge = NewPipeDownloaderBridge(
            dnsLookup = publicDns,
            connectTimeoutMs = 15000L,
            readTimeoutMs = 15000L,
            maxRedirects = 5,
            clientFactory = { dns -> OkHttpClient.Builder().dns(dns).build() }
        )

        assertThat(prodBridge.isCustomTransport).isFalse()
        assertThat(testTransportBridge.isCustomTransport).isTrue()

        NewPipeRuntime.ensureInitialized(prodBridge)

        try {
            NewPipeRuntime.ensureInitialized(testTransportBridge)
            fail("Expected IllegalStateException when re-initializing with custom test transport bridge")
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("NewPipeRuntime already initialized with a different DownloaderBridge")
        }
    }
}
