package com.personaltool.media.extractor.api

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress

class RealHttpStreamDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val publicDns = DnsLookup { listOf(InetAddress.getByName("93.184.216.34")) }

    private fun createValidMp4Payload(): ByteArray {
        val header = byteArrayOf(0x00, 0x00, 0x00, 0x20, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte())
        val body = ByteArray(4096) { (it % 250).toByte() }
        return header + body
    }

    private fun createValidMp3Payload(): ByteArray {
        val header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0x03, 0x00, 0x00, 0x00, 0x00)
        val body = ByteArray(4096) { (it % 250).toByte() }
        return header + body
    }

    private fun createFakeEngine(
        payload: ByteArray,
        contentLength: Long = payload.size.toLong(),
        contentType: String = "video/mp4",
        responseCode: Int = 200,
        customStreamProvider: (() -> InputStream)? = null
    ): SafeHttpTransportEngine = object : SafeHttpTransportEngine {
        override fun openSafeConnection(
            initialUrl: String,
            method: String,
            headers: Map<String, String>,
            maxRedirects: Int,
            connectTimeoutMs: Long,
            readTimeoutMs: Long,
            dnsLookup: DnsLookup
        ): AppResult<SafeHttpResponse> {
            val stream: InputStream = customStreamProvider?.invoke() ?: ByteArrayInputStream(payload)
            return AppResult.Success(
                SafeHttpResponse(
                    response = null,
                    responseBodyStream = stream,
                    contentLength = contentLength,
                    contentType = contentType,
                    finalUrl = initialUrl,
                    responseCode = responseCode,
                    redirectCount = 0
                )
            )
        }
    }

    @Test
    fun successfulSmallMp4Download_validatesContent_computesHash_andCommitsCanonically() = runTest {
        val payload = createValidMp4Payload()
        val fakeEngine = createFakeEngine(payload)
        val downloader = RealHttpStreamDownloader(dnsLookup = publicDns, transportEngine = fakeEngine)
        val destFile = File(tempFolder.root, "video.mp4")

        var progressReported = false
        val result = downloader.download(
            downloadId = "mp4-1",
            sourceUrl = "https://cdn.example.com/video.mp4",
            destinationFile = destFile,
            onProgress = { progress ->
                progressReported = true
                assertThat(progress.bytesDownloaded).isAtLeast(0L)
            }
        )

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val success = (result as AppResult.Success).data
        assertThat(success.file.exists()).isTrue()
        assertThat(success.file.name).isEqualTo("video.mp4")
        assertThat(success.containerType).isEqualTo(DetectedContainer.MP4_ISO_BMFF)
        assertThat(success.detectedMimeType).isEqualTo("video/mp4")
        assertThat(success.fileSizeBytes).isEqualTo(payload.size.toLong())
        assertThat(success.sha256Hex).isNotEmpty()
        assertThat(progressReported).isTrue()

        // Staging file must NOT remain
        val staging = File(tempFolder.root, "video.mp4.part")
        assertThat(staging.exists()).isFalse()
    }

    @Test
    fun successfulSmallAudioMp3Download_succeedsWithAudioMime() = runTest {
        val payload = createValidMp3Payload()
        val fakeEngine = createFakeEngine(payload, contentType = "audio/mpeg")
        val downloader = RealHttpStreamDownloader(dnsLookup = publicDns, transportEngine = fakeEngine)
        val destFile = File(tempFolder.root, "track.mp3")

        val result = downloader.download(
            downloadId = "mp3-1",
            sourceUrl = "https://cdn.example.com/track.mp3",
            destinationFile = destFile,
            onProgress = {}
        )

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val success = (result as AppResult.Success).data
        assertThat(success.containerType).isEqualTo(DetectedContainer.MP3)
        assertThat(success.detectedMimeType).isEqualTo("audio/mpeg")
    }

    @Test
    fun unknownContentLength_supportedTruthfullyWithoutFabricatingExpectedSize() = runTest {
        val payload = createValidMp4Payload()
        val fakeEngine = createFakeEngine(payload, contentLength = -1L)
        val downloader = RealHttpStreamDownloader(dnsLookup = publicDns, transportEngine = fakeEngine)
        val destFile = File(tempFolder.root, "chunked.mp4")

        val result = downloader.download(
            downloadId = "chunked-1",
            sourceUrl = "https://cdn.example.com/chunked.mp4",
            destinationFile = destFile,
            onProgress = {}
        )

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val success = (result as AppResult.Success).data
        assertThat(success.fileSizeBytes).isEqualTo(payload.size.toLong())
        assertThat(destFile.exists()).isTrue()
    }

    @Test
    fun truncatedResponse_contentLengthMismatch_failsClosedAndCleansStaging() = runTest {
        val payload = createValidMp4Payload().copyOfRange(0, 2000) // truncated
        // Promised 10000 bytes, delivered only 2000
        val fakeEngine = createFakeEngine(payload, contentLength = 10000L)
        val downloader = RealHttpStreamDownloader(dnsLookup = publicDns, transportEngine = fakeEngine)
        val destFile = File(tempFolder.root, "truncated.mp4")

        val result = downloader.download(
            downloadId = "trunc-1",
            sourceUrl = "https://cdn.example.com/truncated.mp4",
            destinationFile = destFile,
            onProgress = {}
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.NETWORK_ERROR)
        assertThat(error.message).contains("Premature EOF")
        assertThat(destFile.exists()).isFalse()

        val staging = File(tempFolder.root, "truncated.mp4.part")
        assertThat(staging.exists()).isFalse()
    }

    @Test
    fun emptyStreamZeroBytes_failsClosed() = runTest {
        val fakeEngine = createFakeEngine(ByteArray(0), contentLength = 0L)
        val downloader = RealHttpStreamDownloader(dnsLookup = publicDns, transportEngine = fakeEngine)
        val destFile = File(tempFolder.root, "empty.mp4")

        val result = downloader.download(
            downloadId = "empty-1",
            sourceUrl = "https://cdn.example.com/empty.mp4",
            destinationFile = destFile,
            onProgress = {}
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.NETWORK_ERROR)
        assertThat(destFile.exists()).isFalse()
    }

    @Test
    fun htmlDisguisedAsMedia_failsValidationAndCleansStaging() = runTest {
        val html = "<!DOCTYPE html><html><head><title>Login</title></head><body>Login required</body></html>".toByteArray()
        val payload = html + ByteArray(2048) { ' '.code.toByte() }
        val fakeEngine = createFakeEngine(payload)
        val downloader = RealHttpStreamDownloader(dnsLookup = publicDns, transportEngine = fakeEngine)
        val destFile = File(tempFolder.root, "login.mp4")

        val result = downloader.download(
            downloadId = "html-1",
            sourceUrl = "https://cdn.example.com/login.mp4",
            destinationFile = destFile,
            onProgress = {}
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.VALIDATION_ERROR)
        assertThat(error.message).contains("HTML markup")
        assertThat(destFile.exists()).isFalse()

        val staging = File(tempFolder.root, "login.mp4.part")
        assertThat(staging.exists()).isFalse()
    }

    @Test
    fun jsonErrorPayload_failsValidationAndCleansStaging() = runTest {
        val json = """{"status": 403, "error": "Forbidden", "message": "Access denied"}""".toByteArray()
        val payload = json + ByteArray(2048) { ' '.code.toByte() }
        val fakeEngine = createFakeEngine(payload)
        val downloader = RealHttpStreamDownloader(dnsLookup = publicDns, transportEngine = fakeEngine)
        val destFile = File(tempFolder.root, "error.mp4")

        val result = downloader.download(
            downloadId = "json-1",
            sourceUrl = "https://cdn.example.com/error.mp4",
            destinationFile = destFile,
            onProgress = {}
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.VALIDATION_ERROR)
        assertThat(error.message).contains("JSON error")
        assertThat(destFile.exists()).isFalse()
    }

    @Test
    fun destinationCollision_withFailIfExists_rejectsAndPreservesOriginal() = runTest {
        val payload = createValidMp4Payload()
        val fakeEngine = createFakeEngine(payload)
        val downloader = RealHttpStreamDownloader(dnsLookup = publicDns, transportEngine = fakeEngine)
        val destFile = File(tempFolder.root, "existing.mp4")
        destFile.writeText("ORIGINAL_CONTENT")

        val result = downloader.download(
            downloadId = "col-1",
            sourceUrl = "https://cdn.example.com/existing.mp4",
            destinationFile = destFile,
            collisionPolicy = DestinationCollisionPolicy.FAIL_IF_EXISTS,
            onProgress = {}
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.STORAGE_ERROR)
        assertThat(error.message).contains("already exists")
        assertThat(destFile.readText()).isEqualTo("ORIGINAL_CONTENT")
    }

    @Test
    fun cancellation_cleansStagingFileImmediately() = runTest {
        val payload = createValidMp4Payload()
        lateinit var downloaderRef: RealHttpStreamDownloader

        // Custom stream that triggers cancel on read
        val cancellingStream = object : InputStream() {
            private val wrapped = ByteArrayInputStream(payload)
            override fun read(): Int {
                downloaderRef.cancel("cancel-1")
                return wrapped.read()
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                downloaderRef.cancel("cancel-1")
                return wrapped.read(b, off, len)
            }
        }

        val fakeEngine = createFakeEngine(payload, customStreamProvider = { cancellingStream })
        val downloader = RealHttpStreamDownloader(dnsLookup = publicDns, transportEngine = fakeEngine)
        downloaderRef = downloader
        val destFile = File(tempFolder.root, "cancelled.mp4")

        val result = downloader.download(
            downloadId = "cancel-1",
            sourceUrl = "https://cdn.example.com/cancelled.mp4",
            destinationFile = destFile,
            onProgress = {}
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.NETWORK_ERROR)
        assertThat(error.message).contains("cancelled")
        assertThat(destFile.exists()).isFalse()

        val staging = File(tempFolder.root, "cancelled.mp4.part")
        assertThat(staging.exists()).isFalse()
    }

    @Test
    fun headRejected_boundedGetProbe_succeeds() = runTest {
        val payload = createValidMp4Payload()
        var headCalled = false
        var getCalled = false

        val fakeEngine = object : SafeHttpTransportEngine {
            override fun openSafeConnection(
                initialUrl: String,
                method: String,
                headers: Map<String, String>,
                maxRedirects: Int,
                connectTimeoutMs: Long,
                readTimeoutMs: Long,
                dnsLookup: DnsLookup
            ): AppResult<SafeHttpResponse> {
                return if (method == "HEAD") {
                    headCalled = true
                    AppResult.Error("HEAD 405 Method Not Allowed", code = ErrorCode.NETWORK_ERROR)
                } else {
                    getCalled = true
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(payload),
                            contentLength = payload.size.toLong(),
                            contentType = "video/mp4",
                            finalUrl = initialUrl,
                            responseCode = 206,
                            redirectCount = 0
                        )
                    )
                }
            }
        }

        val probeResult = HttpMediaProber.probeDirectMediaUrl(
            urlString = "https://cdn.example.com/video.mp4",
            dnsLookup = publicDns,
            transportEngine = fakeEngine
        )

        assertThat(probeResult).isInstanceOf(AppResult.Success::class.java)
        val probe = (probeResult as AppResult.Success).data
        assertThat(headCalled).isTrue()
        assertThat(getCalled).isTrue()
        assertThat(probe.contentLength).isEqualTo(payload.size.toLong())
        assertThat(probe.contentType).isEqualTo("video/mp4")
    }

    @Test
    fun extractor_probeYouTubeUrl_failsClosedWithPlatformExtractionUnavailable() = runTest {
        val extractor = DefaultMediaExtractor(dnsLookup = publicDns)
        val result = extractor.probeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("PLATFORM_EXTRACTION_UNAVAILABLE")
        assertThat(error.code).isEqualTo(ErrorCode.EXTRACTION_FAILED)
    }

    @Test
    fun extractor_probeInstagramUrl_failsClosedWithPlatformExtractionUnavailable() = runTest {
        val extractor = DefaultMediaExtractor(dnsLookup = publicDns)
        val result = extractor.probeUrl("https://www.instagram.com/reel/C1234567890/")
        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("PLATFORM_EXTRACTION_UNAVAILABLE")
        assertThat(error.code).isEqualTo(ErrorCode.EXTRACTION_FAILED)
    }

    @Test
    fun extractor_probeXTwitterUrl_failsClosedWithPlatformExtractionUnavailable() = runTest {
        val extractor = DefaultMediaExtractor(dnsLookup = publicDns)
        val result = extractor.probeUrl("https://x.com/tech_user/status/987654321")
        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("PLATFORM_EXTRACTION_UNAVAILABLE")
        assertThat(error.code).isEqualTo(ErrorCode.EXTRACTION_FAILED)
    }
}
