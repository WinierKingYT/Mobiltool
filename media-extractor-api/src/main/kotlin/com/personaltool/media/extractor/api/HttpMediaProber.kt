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

    fun probeDirectMediaUrl(
        urlString: String,
        dnsLookup: DnsLookup = SystemDnsLookup,
        transportEngine: SafeHttpTransportEngine = SafeHttpTransport
    ): AppResult<HttpProbeResult> {
        // 1. Initial HEAD probe (Optional metadata collection only)
        val headResult = transportEngine.openSafeConnection(
            initialUrl = urlString,
            method = "HEAD",
            dnsLookup = dnsLookup
        )

        var headContentLength = -1L
        var headContentType: String? = null
        var headDisposition: String? = null

        when (headResult) {
            is AppResult.Success -> {
                val response = headResult.data
                try {
                    // Check for explicit non-media Content-Type -> FAIL CLOSED IMMEDIATELY
                    if (isExplicitNonMediaContentType(response.contentType)) {
                        return AppResult.Error(
                            message = "Direct URL probe rejected: Server returned non-media Content-Type '${response.contentType}' (HTML/JSON/text)",
                            code = ErrorCode.VALIDATION_ERROR
                        )
                    }

                    if (response.responseCode in 200..299) {
                        headContentLength = response.contentLength
                        headContentType = response.contentType
                        headDisposition = response.response?.header("Content-Disposition")
                    }
                } finally {
                    response.close()
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

        // 2. Bounded GET Range probe: Inspect first bytes to prove media container (MANDATORY PAYLOAD PROOF)
        var getResult = transportEngine.openSafeConnection(
            initialUrl = urlString,
            method = "GET",
            headers = mapOf("Range" to "bytes=0-4095"),
            dnsLookup = dnsLookup
        )

        // If server rejected Range with 405/416/501, fallback to bounded normal GET
        if (getResult is AppResult.Success && getResult.data.responseCode in listOf(405, 416, 501)) {
            getResult.data.close()
            getResult = transportEngine.openSafeConnection(
                initialUrl = urlString,
                method = "GET",
                headers = emptyMap(),
                dnsLookup = dnsLookup
            )
        }

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
                    } else if (headContentLength > 0) {
                        contentLength = headContentLength
                    }

                    // Read strictly bounded header prefix from stream (up to 768 bytes)
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

                    // Inspect header bytes - PAYLOAD PROOF IS MANDATORY (NO HEAD OVERRIDES)
                    when (val headerInspection = MediaFileValidator.inspectHeaderBytes(headerBuffer, totalBytesRead)) {
                        is MediaFileValidator.HeaderValidationResult.ValidMedia -> {
                            val provenExt = headerInspection.defaultExtension
                            val observedContentType = response.contentType ?: headContentType ?: headerInspection.mimeType
                            val suggestedFileName = extractFileName(
                                urlString = response.finalUrl,
                                contentDisposition = okResponse?.header("Content-Disposition") ?: headDisposition,
                                provenExtension = provenExt
                            )

                            AppResult.Success(
                                HttpProbeResult(
                                    contentLength = contentLength,
                                    contentType = observedContentType,
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
                            // PAYLOAD PROOF FAILED: NEVER OVERRIDE WITH HEAD CONTENT-TYPE
                            AppResult.Error(
                                message = "Direct URL probe rejected: ${headerInspection.reason}",
                                code = ErrorCode.VALIDATION_ERROR
                            )
                        }
                    }
                } finally {
                    response.close()
                }
            }
            is AppResult.Error -> {
                // GET PROBE FAILED: NEVER FALLBACK TO HEAD-ONLY SUCCESS
                getResult
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
