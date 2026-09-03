package com.personaltool.media.extractor.api

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.InetAddress

class HttpMediaProberTest {

    private val publicDns = DnsLookup { listOf(InetAddress.getByName("93.184.216.34")) }

    private fun createValidMp4Header(): ByteArray {
        val header = ByteArray(768)
        header[4] = 'f'.code.toByte()
        header[5] = 't'.code.toByte()
        header[6] = 'y'.code.toByte()
        header[7] = 'p'.code.toByte()
        header[8] = 'i'.code.toByte()
        header[9] = 's'.code.toByte()
        header[10] = 'o'.code.toByte()
        header[11] = 'm'.code.toByte()
        return header
    }

    private fun createValidMp3Header(): ByteArray {
        val header = ByteArray(768)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        return header
    }

    @Test
    fun extractFileName_withRfc5987ContentDisposition_decodesUtf8Correctly() {
        val cd = "attachment; filename*=UTF-8''video%20test%20%C3%A7%C4%B1nar.mp4"
        val fileName = HttpMediaProber.extractFileName("https://example.com/stream", cd, "mp4")
        assertThat(fileName).contains(".mp4")
    }

    @Test
    fun extractFileName_withStandardContentDisposition_sanitizesCharacters() {
        val cd = """attachment; filename="evil/path:test*file?.mp4""""
        val fileName = HttpMediaProber.extractFileName("https://example.com/stream", cd, "mp4")
        assertThat(fileName).doesNotContain("/")
        assertThat(fileName).doesNotContain(":")
        assertThat(fileName).doesNotContain("*")
        assertThat(fileName).doesNotContain("?")
    }

    @Test
    fun extractFileName_withUrlPath_derivesFromLastSegment() {
        val url = "https://cdn.example.com/videos/2026/presentation_hd.webm"
        val fileName = HttpMediaProber.extractFileName(url, null, "webm")
        assertThat(fileName).isEqualTo("presentation_hd.webm")
    }

    @Test
    fun extractFileName_fallbackUsesTimestampAndProvenExtension() {
        val url = "https://cdn.example.com/stream"
        val fileName = HttpMediaProber.extractFileName(url, null, "mp3")
        assertThat(fileName).startsWith("media_download_")
        assertThat(fileName).endsWith(".mp3")
    }

    @Test
    fun extractFileName_fallbackWithNullProvenExtension_hasNoExtension() {
        val url = "https://cdn.example.com/stream"
        val fileName = HttpMediaProber.extractFileName(url, null, null)
        assertThat(fileName).startsWith("media_download_")
        assertThat(fileName).doesNotContain(".")
    }

    @Test
    fun isExplicitNonMediaContentType_detectsHtmlJsonTextImages() {
        assertThat(HttpMediaProber.isExplicitNonMediaContentType("text/html; charset=utf-8")).isTrue()
        assertThat(HttpMediaProber.isExplicitNonMediaContentType("application/json")).isTrue()
        assertThat(HttpMediaProber.isExplicitNonMediaContentType("text/plain")).isTrue()
        assertThat(HttpMediaProber.isExplicitNonMediaContentType("application/xml")).isTrue()
        assertThat(HttpMediaProber.isExplicitNonMediaContentType("application/javascript")).isTrue()
        assertThat(HttpMediaProber.isExplicitNonMediaContentType("image/png")).isTrue()

        assertThat(HttpMediaProber.isExplicitNonMediaContentType("video/mp4")).isFalse()
        assertThat(HttpMediaProber.isExplicitNonMediaContentType("audio/mpeg")).isFalse()
        assertThat(HttpMediaProber.isExplicitNonMediaContentType("application/octet-stream")).isFalse()
        assertThat(HttpMediaProber.isExplicitNonMediaContentType(null)).isFalse()
    }

    @Test
    fun inspectHeaderBytes_validMp4_returnsValidMedia() {
        val header = createValidMp4Header()
        val result = MediaFileValidator.inspectHeaderBytes(header, header.size)
        assertThat(result).isInstanceOf(MediaFileValidator.HeaderValidationResult.ValidMedia::class.java)
        val valid = result as MediaFileValidator.HeaderValidationResult.ValidMedia
        assertThat(valid.container).isEqualTo(DetectedContainer.MP4_ISO_BMFF)
        assertThat(valid.mediaKind).isEqualTo(DetectedMediaKind.UNKNOWN)
        assertThat(valid.defaultExtension).isEqualTo("mp4")
    }

    @Test
    fun inspectHeaderBytes_htmlDoctype_returnsInvalid() {
        val html = "<!DOCTYPE html><html><head><title>Login</title></head><body>Login required</body></html>".toByteArray()
        val result = MediaFileValidator.inspectHeaderBytes(html, html.size)
        assertThat(result).isInstanceOf(MediaFileValidator.HeaderValidationResult.Invalid::class.java)
        val invalid = result as MediaFileValidator.HeaderValidationResult.Invalid
        assertThat(invalid.reason).contains("HTML")
    }

    @Test
    fun inspectHeaderBytes_jsonError_returnsInvalid() {
        val json = """{"error": "Unauthorized", "message": "API key required"}""".toByteArray()
        val result = MediaFileValidator.inspectHeaderBytes(json, json.size)
        assertThat(result).isInstanceOf(MediaFileValidator.HeaderValidationResult.Invalid::class.java)
        val invalid = result as MediaFileValidator.HeaderValidationResult.Invalid
        assertThat(invalid.reason).contains("JSON")
    }

    @Test
    fun inspectHeaderBytes_unknownBinary_returnsInvalid() {
        val randomBinary = ByteArray(768) { it.toByte() }
        val result = MediaFileValidator.inspectHeaderBytes(randomBinary, randomBinary.size)
        assertThat(result).isInstanceOf(MediaFileValidator.HeaderValidationResult.Invalid::class.java)
        val invalid = result as MediaFileValidator.HeaderValidationResult.Invalid
        assertThat(invalid.reason).contains("Unrecognized binary container")
    }

    @Test
    fun probeDirectMediaUrl_headReturnsHtml_failsClosedImmediately() {
        val fakeTransport = object : SafeHttpTransportEngine {
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
                        responseBodyStream = ByteArrayInputStream(ByteArray(0)),
                        contentLength = 1500L,
                        contentType = "text/html; charset=UTF-8",
                        requestedUrl = initialUrl,
                        finalUrl = initialUrl,
                        responseCode = 200,
                        redirectCount = 0
                    )
                )
            }
        }

        val result = HttpMediaProber.probeDirectMediaUrl(
            urlString = "https://example.com/watch.php",
            dnsLookup = publicDns,
            transportEngine = fakeTransport
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("non-media Content-Type 'text/html")
        assertThat(error.code).isEqualTo(ErrorCode.VALIDATION_ERROR)
    }

    @Test
    fun probeDirectMediaUrl_headReturnsJson_failsClosedImmediately() {
        val fakeTransport = object : SafeHttpTransportEngine {
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
                        responseBodyStream = ByteArrayInputStream(ByteArray(0)),
                        contentLength = 500L,
                        contentType = "application/json",
                        requestedUrl = initialUrl,
                        finalUrl = initialUrl,
                        responseCode = 200,
                        redirectCount = 0
                    )
                )
            }
        }

        val result = HttpMediaProber.probeDirectMediaUrl(
            urlString = "https://api.example.com/video/metadata",
            dnsLookup = publicDns,
            transportEngine = fakeTransport
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("non-media Content-Type 'application/json'")
        assertThat(error.code).isEqualTo(ErrorCode.VALIDATION_ERROR)
    }

    @Test
    fun probeDirectMediaUrl_headOctetStream_getHtmlPrefix_failsClosed() {
        val htmlPayload = "<!doctype html><html><body>Error 403 Forbidden</body></html>".toByteArray()
        val fakeTransport = object : SafeHttpTransportEngine {
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
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(ByteArray(0)),
                            contentLength = htmlPayload.size.toLong(),
                            contentType = "application/octet-stream",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 200,
                            redirectCount = 0
                        )
                    )
                } else {
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(htmlPayload),
                            contentLength = htmlPayload.size.toLong(),
                            contentType = "application/octet-stream",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 206,
                            redirectCount = 0
                        )
                    )
                }
            }
        }

        val result = HttpMediaProber.probeDirectMediaUrl(
            urlString = "https://cdn.example.com/video.mp4",
            dnsLookup = publicDns,
            transportEngine = fakeTransport
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("HTML")
        assertThat(error.code).isEqualTo(ErrorCode.VALIDATION_ERROR)
    }

    @Test
    fun probeDirectMediaUrl_headOctetStream_unknownBinaryPrefix_failsClosed() {
        val randomPayload = ByteArray(1024) { 0x55.toByte() }
        val fakeTransport = object : SafeHttpTransportEngine {
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
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(ByteArray(0)),
                            contentLength = randomPayload.size.toLong(),
                            contentType = "application/octet-stream",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 200,
                            redirectCount = 0
                        )
                    )
                } else {
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(randomPayload),
                            contentLength = randomPayload.size.toLong(),
                            contentType = "application/octet-stream",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 206,
                            redirectCount = 0
                        )
                    )
                }
            }
        }

        val result = HttpMediaProber.probeDirectMediaUrl(
            urlString = "https://cdn.example.com/unknown_file.bin",
            dnsLookup = publicDns,
            transportEngine = fakeTransport
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("Unrecognized binary container")
        assertThat(error.code).isEqualTo(ErrorCode.VALIDATION_ERROR)
    }

    @Test
    fun probeDirectMediaUrl_headVideoMp4_getRandomBinary_failsValidation() {
        val randomPayload = ByteArray(1024) { 0x33.toByte() }
        val fakeTransport = object : SafeHttpTransportEngine {
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
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(ByteArray(0)),
                            contentLength = 1048576L,
                            contentType = "video/mp4",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 200,
                            redirectCount = 0
                        )
                    )
                } else {
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(randomPayload),
                            contentLength = 1048576L,
                            contentType = "video/mp4",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 206,
                            redirectCount = 0
                        )
                    )
                }
            }
        }

        val result = HttpMediaProber.probeDirectMediaUrl(
            urlString = "https://cdn.example.com/fake_video.mp4",
            dnsLookup = publicDns,
            transportEngine = fakeTransport
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("Unrecognized binary container")
        assertThat(error.code).isEqualTo(ErrorCode.VALIDATION_ERROR)
    }

    @Test
    fun probeDirectMediaUrl_headAudioMpeg_getRandomBinary_failsValidation() {
        val randomPayload = ByteArray(1024) { 0x44.toByte() }
        val fakeTransport = object : SafeHttpTransportEngine {
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
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(ByteArray(0)),
                            contentLength = 524288L,
                            contentType = "audio/mpeg",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 200,
                            redirectCount = 0
                        )
                    )
                } else {
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(randomPayload),
                            contentLength = 524288L,
                            contentType = "audio/mpeg",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 206,
                            redirectCount = 0
                        )
                    )
                }
            }
        }

        val result = HttpMediaProber.probeDirectMediaUrl(
            urlString = "https://cdn.example.com/fake_audio.mp3",
            dnsLookup = publicDns,
            transportEngine = fakeTransport
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("Unrecognized binary container")
        assertThat(error.code).isEqualTo(ErrorCode.VALIDATION_ERROR)
    }

    @Test
    fun probeDirectMediaUrl_headVideoWebm_getUnknownBinary_failsValidation() {
        val randomPayload = ByteArray(1024) { 0x77.toByte() }
        val fakeTransport = object : SafeHttpTransportEngine {
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
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(ByteArray(0)),
                            contentLength = 2097152L,
                            contentType = "video/webm",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 200,
                            redirectCount = 0
                        )
                    )
                } else {
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(randomPayload),
                            contentLength = 2097152L,
                            contentType = "video/webm",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 206,
                            redirectCount = 0
                        )
                    )
                }
            }
        }

        val result = HttpMediaProber.probeDirectMediaUrl(
            urlString = "https://cdn.example.com/fake_video.webm",
            dnsLookup = publicDns,
            transportEngine = fakeTransport
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("Unrecognized binary container")
        assertThat(error.code).isEqualTo(ErrorCode.VALIDATION_ERROR)
    }

    @Test
    fun probeDirectMediaUrl_headVideoMp4_getNetworkFailure_failsNetworkError() {
        val fakeTransport = object : SafeHttpTransportEngine {
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
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(ByteArray(0)),
                            contentLength = 1048576L,
                            contentType = "video/mp4",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 200,
                            redirectCount = 0
                        )
                    )
                } else {
                    AppResult.Error(
                        message = "Connection reset by peer during payload probe",
                        code = ErrorCode.NETWORK_ERROR
                    )
                }
            }
        }

        val result = HttpMediaProber.probeDirectMediaUrl(
            urlString = "https://cdn.example.com/video.mp4",
            dnsLookup = publicDns,
            transportEngine = fakeTransport
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("Connection reset by peer")
        assertThat(error.code).isEqualTo(ErrorCode.NETWORK_ERROR)
    }

    @Test
    fun probeDirectMediaUrl_validMp4_succeedsWithProvenExtension() {
        val mp4Payload = createValidMp4Header()
        val fakeTransport = object : SafeHttpTransportEngine {
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
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(ByteArray(0)),
                            contentLength = 1048576L,
                            contentType = "video/mp4",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 200,
                            redirectCount = 0
                        )
                    )
                } else {
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(mp4Payload),
                            contentLength = 1048576L,
                            contentType = "video/mp4",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 206,
                            redirectCount = 0
                        )
                    )
                }
            }
        }

        val result = HttpMediaProber.probeDirectMediaUrl(
            urlString = "https://cdn.example.com/clip.mp4",
            dnsLookup = publicDns,
            transportEngine = fakeTransport
        )

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val probe = (result as AppResult.Success).data
        assertThat(probe.containerType).isEqualTo(DetectedContainer.MP4_ISO_BMFF)
        assertThat(probe.provenExtension).isEqualTo("mp4")
        assertThat(probe.contentLength).isEqualTo(1048576L)
    }

    @Test
    fun probeDirectMediaUrl_headOctetStream_validMp3Audio_succeedsWithAudioKindAndProvenExtension() {
        val mp3Payload = createValidMp3Header()
        val fakeTransport = object : SafeHttpTransportEngine {
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
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(ByteArray(0)),
                            contentLength = 524288L,
                            contentType = "application/octet-stream",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 200,
                            redirectCount = 0
                        )
                    )
                } else {
                    AppResult.Success(
                        SafeHttpResponse(
                            response = null,
                            responseBodyStream = ByteArrayInputStream(mp3Payload),
                            contentLength = 524288L,
                            contentType = "application/octet-stream",
                            requestedUrl = initialUrl,
                            finalUrl = initialUrl,
                            responseCode = 206,
                            redirectCount = 0
                        )
                    )
                }
            }
        }

        val result = HttpMediaProber.probeDirectMediaUrl(
            urlString = "https://cdn.example.com/song.mp3",
            dnsLookup = publicDns,
            transportEngine = fakeTransport
        )

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val probe = (result as AppResult.Success).data
        assertThat(probe.containerType).isEqualTo(DetectedContainer.MP3)
        assertThat(probe.mediaKind).isEqualTo(DetectedMediaKind.AUDIO)
        assertThat(probe.provenExtension).isEqualTo("mp3")
        assertThat(probe.contentLength).isEqualTo(524288L)
    }
}
