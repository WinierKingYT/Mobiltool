package com.personaltool.media.extractor.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import java.net.URI

data class HttpProbeResult(
    val contentLength: Long,
    val contentType: String?,
    val isRangeSupported: Boolean,
    val suggestedFileName: String,
    val finalResolvedUrl: String,
    val containerType: DetectedContainer,
    val mediaKind: DetectedMediaKind,
    val verifiedMimeType: String?,
    val provenExtension: String?
)

object HttpMediaProber {

    fun isExplicitNonMediaContentType(contentType: String?): Boolean {
        if (contentType.isNullOrBlank()) return false
        val clean = contentType.substringBefore(';').trim().lowercase()
        return clean.startsWith("text/") ||
                clean == "application/json" ||
                clean == "application/xml" ||
                clean == "application/javascript" ||
                clean == "application/x-javascript" ||
                clean == "application/pdf" ||
                clean == "application/zip" ||
                clean == "application/x-zip-compressed" ||
                clean.startsWith("image/")
    }

    fun mapContentTypeToDetection(contentType: String): MediaFileValidator.ContainerDetection? {
        val clean = contentType.substringBefore(';').trim().lowercase()
        return when (clean) {
            "video/mp4", "video/quicktime" -> MediaFileValidator.ContainerDetection(
                container = DetectedContainer.MP4_ISO_BMFF,
                mediaKind = DetectedMediaKind.UNKNOWN,
                mimeType = clean,
                defaultExtension = "mp4"
            )
            "audio/mp4" -> MediaFileValidator.ContainerDetection(
                container = DetectedContainer.MP4_ISO_BMFF,
                mediaKind = DetectedMediaKind.AUDIO,
                mimeType = "audio/mp4",
                defaultExtension = "m4a"
            )
            "video/webm" -> MediaFileValidator.ContainerDetection(
                container = DetectedContainer.MATROSKA_WEBM,
                mediaKind = DetectedMediaKind.UNKNOWN,
                mimeType = clean,
                defaultExtension = "webm"
            )
            "video/x-matroska" -> MediaFileValidator.ContainerDetection(
                container = DetectedContainer.MATROSKA_WEBM,
                mediaKind = DetectedMediaKind.UNKNOWN,
                mimeType = clean,
                defaultExtension = "mkv"
            )
            "audio/mpeg", "audio/mp3" -> MediaFileValidator.ContainerDetection(
                container = DetectedContainer.MP3,
                mediaKind = DetectedMediaKind.AUDIO,
                mimeType = "audio/mpeg",
                defaultExtension = "mp3"
            )
            "audio/ogg", "application/ogg" -> MediaFileValidator.ContainerDetection(
                container = DetectedContainer.OGG,
                mediaKind = DetectedMediaKind.UNKNOWN,
                mimeType = clean,
                defaultExtension = "ogg"
            )
            "audio/wav", "audio/x-wav", "audio/wave" -> MediaFileValidator.ContainerDetection(
                container = DetectedContainer.WAV,
                mediaKind = DetectedMediaKind.AUDIO,
                mimeType = "audio/wav",
                defaultExtension = "wav"
            )
            "audio/flac", "audio/x-flac" -> MediaFileValidator.ContainerDetection(
                container = DetectedContainer.FLAC,
                mediaKind = DetectedMediaKind.AUDIO,
                mimeType = "audio/flac",
                defaultExtension = "flac"
            )
            "video/mp2t" -> MediaFileValidator.ContainerDetection(
                container = DetectedContainer.MPEG_TS,
                mediaKind = DetectedMediaKind.UNKNOWN,
                mimeType = clean,
                defaultExtension = "ts"
            )
            else -> null
        }
    }

    fun probeDirectMediaUrl(
        urlString: String,
        dnsLookup: DnsLookup = SystemDnsLookup,
        transportEngine: SafeHttpTransportEngine = SafeHttpTransport
    ): AppResult<HttpProbeResult> {
        // 1. Initial HEAD probe
        val headResult = transportEngine.openSafeConnection(
            initialUrl = urlString,
            method = "HEAD",
            dnsLookup = dnsLookup
        )

        var headResponse: SafeHttpResponse? = null
        var isHeadConclusive = false
        var headDetection: MediaFileValidator.ContainerDetection? = null

        when (headResult) {
            is AppResult.Success -> {
                val response = headResult.data
                headResponse = response

                // Check for explicit non-media Content-Type -> FAIL CLOSED IMMEDIATELY
                if (isExplicitNonMediaContentType(response.contentType)) {
                    response.close()
                    return AppResult.Error(
                        message = "Direct URL probe rejected: Server returned non-media Content-Type '${response.contentType}' (HTML/JSON/text)",
                        code = ErrorCode.VALIDATION_ERROR
                    )
                }

                val code = response.responseCode
                if (code in 200..299 && !response.contentType.isNullOrBlank()) {
                    headDetection = mapContentTypeToDetection(response.contentType)
                    if (headDetection != null) {
                        isHeadConclusive = true
                    }
                }
            }
            is AppResult.Error -> {
                // If validation / security failed, return immediately without fallback
                if (headResult.code == ErrorCode.SECURITY_VIOLATION || headResult.code == ErrorCode.VALIDATION_ERROR) {
                    return headResult
                }
            }
            AppResult.Loading -> {}
        }

        // 2. Bounded GET Range probe: Inspect first bytes to prove media container (P2-DIRECT-TRUTH-FINAL-01)
        val getResult = transportEngine.openSafeConnection(
            initialUrl = urlString,
            method = "GET",
            headers = mapOf("Range" to "bytes=0-4095"),
            dnsLookup = dnsLookup
        )

        return when (getResult) {
            is AppResult.Success -> {
                val response = getResult.data
                try {
                    val code = response.responseCode
                    if (code !in 200..299 && code != 206) {
                        return AppResult.Error(
                            message = "Server returned HTTP $code for media probe",
                            code = ErrorCode.NETWORK_ERROR
                        )
                    }

                    // Check GET response Content-Type
                    if (isExplicitNonMediaContentType(response.contentType)) {
                        return AppResult.Error(
                            message = "Direct URL probe rejected: Server returned non-media Content-Type '${response.contentType}' (HTML/JSON/text)",
                            code = ErrorCode.VALIDATION_ERROR
                        )
                    }

                    val okResponse = response.response
                    var contentLength = response.contentLength
                    val contentRange = okResponse?.header("Content-Range")
                    val isRangeSupported = code == 206 || contentRange != null

                    if (!contentRange.isNullOrBlank()) {
                        val totalStr = contentRange.substringAfterLast('/')
                        val parsedTotal = totalStr.toLongOrNull()
                        if (parsedTotal != null && parsedTotal > 0) {
                            contentLength = parsedTotal
                        }
                    } else if (headResponse != null && headResponse.contentLength > 0) {
                        contentLength = headResponse.contentLength
                    }

                    // Read bounded header prefix from stream (up to 768 bytes)
                    val headerBuffer = ByteArray(768)
                    var totalBytesRead = 0
                    runCatching {
                        response.responseBodyStream.use { stream ->
                            while (totalBytesRead < headerBuffer.size) {
                                val read = stream.read(headerBuffer, totalBytesRead, headerBuffer.size - totalBytesRead)
                                if (read == -1) break
                                totalBytesRead += read
                            }
                        }
                    }

                    // Inspect header bytes
                    val headerInspection = MediaFileValidator.inspectHeaderBytes(headerBuffer, totalBytesRead)
                    when (headerInspection) {
                        is MediaFileValidator.HeaderValidationResult.ValidMedia -> {
                            val provenExt = headerInspection.defaultExtension
                            val finalContentType = response.contentType ?: headerInspection.mimeType
                            val suggestedFileName = extractFileName(
                                urlString = response.finalUrl,
                                contentDisposition = okResponse?.header("Content-Disposition"),
                                provenExtension = provenExt
                            )

                            AppResult.Success(
                                HttpProbeResult(
                                    contentLength = contentLength,
                                    contentType = finalContentType,
                                    isRangeSupported = isRangeSupported,
                                    suggestedFileName = suggestedFileName,
                                    finalResolvedUrl = response.finalUrl,
                                    containerType = headerInspection.container,
                                    mediaKind = headerInspection.mediaKind,
                                    verifiedMimeType = headerInspection.mimeType,
                                    provenExtension = provenExt
                                )
                            )
                        }
                        is MediaFileValidator.HeaderValidationResult.Invalid -> {
                            // If header inspection failed, check if HEAD had definitive media Content-Type AND bytes were not HTML/JSON
                            if (isHeadConclusive && headDetection != null &&
                                !headerInspection.reason.contains("HTML", ignoreCase = true) &&
                                !headerInspection.reason.contains("JSON", ignoreCase = true)
                            ) {
                                val provenExt = headDetection.defaultExtension
                                val suggestedFileName = extractFileName(
                                    urlString = response.finalUrl,
                                    contentDisposition = okResponse?.header("Content-Disposition"),
                                    provenExtension = provenExt
                                )

                                AppResult.Success(
                                    HttpProbeResult(
                                        contentLength = contentLength,
                                        contentType = headResponse?.contentType,
                                        isRangeSupported = isRangeSupported,
                                        suggestedFileName = suggestedFileName,
                                        finalResolvedUrl = response.finalUrl,
                                        containerType = headDetection.container,
                                        mediaKind = headDetection.mediaKind,
                                        verifiedMimeType = headDetection.mimeType,
                                        provenExtension = provenExt
                                    )
                                )
                            } else {
                                AppResult.Error(
                                    message = "Direct URL probe rejected: ${headerInspection.reason}",
                                    code = ErrorCode.VALIDATION_ERROR
                                )
                            }
                        }
                    }
                } finally {
                    response.close()
                    headResponse?.close()
                }
            }
            is AppResult.Error -> {
                headResponse?.close()
                // If GET probe failed (e.g. range unsupported or 405) but HEAD had definitive proven media
                if (isHeadConclusive && headResponse != null && headDetection != null) {
                    val probe = HttpProbeResult(
                        contentLength = headResponse.contentLength,
                        contentType = headResponse.contentType,
                        isRangeSupported = false,
                        suggestedFileName = extractFileName(headResponse.finalUrl, null, headDetection.defaultExtension),
                        finalResolvedUrl = headResponse.finalUrl,
                        containerType = headDetection.container,
                        mediaKind = headDetection.mediaKind,
                        verifiedMimeType = headDetection.mimeType,
                        provenExtension = headDetection.defaultExtension
                    )
                    headResponse.close()
                    AppResult.Success(probe)
                } else {
                    getResult
                }
            }
            AppResult.Loading -> AppResult.Loading
        }
    }

    fun extractFileName(
        urlString: String,
        contentDisposition: String?,
        provenExtension: String?
    ): String {
        // 1. Try Content-Disposition header
        if (!contentDisposition.isNullOrBlank()) {
            val rfc5987Regex = Regex("""filename\*=UTF-8''([^';\r\n]+)""", RegexOption.IGNORE_CASE)
            val rfcMatch = rfc5987Regex.find(contentDisposition)
            if (rfcMatch != null) {
                val decoded = runCatching { java.net.URLDecoder.decode(rfcMatch.groupValues[1].trim(), "UTF-8") }.getOrNull()
                if (!decoded.isNullOrBlank()) return sanitizeFileName(decoded)
            }

            val standardRegex = Regex("""filename=['"]?([^'";\r\n]+)['"]?""", RegexOption.IGNORE_CASE)
            val match = standardRegex.find(contentDisposition)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.isNotBlank()) return sanitizeFileName(candidate)
            }
        }

        // 2. Try URL path segment
        val path = runCatching { URI(urlString).path }.getOrNull()
        if (!path.isNullOrBlank()) {
            val lastSegment = path.substringAfterLast('/').substringBefore('?')
            if (lastSegment.isNotBlank() && lastSegment.contains('.') && !lastSegment.startsWith('.')) {
                return sanitizeFileName(lastSegment)
            }
        }

        // 3. Fallback using ONLY proven extension (zero fabrication of mp4!)
        return if (!provenExtension.isNullOrBlank()) {
            "media_download_${System.currentTimeMillis()}.$provenExtension"
        } else {
            "media_download_${System.currentTimeMillis()}"
        }
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_").trim()
        val safe = if (cleaned.startsWith("..") || cleaned.isBlank()) "media_download" else cleaned
        return if (safe.length > 120) safe.take(120) else safe
    }
}
