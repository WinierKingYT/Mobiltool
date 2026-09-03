package com.personaltool.media.extractor.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import java.net.URI

data class HttpProbeResult(
    val contentLength: Long,
    val contentType: String?,
    val isRangeSupported: Boolean,
    val suggestedFileName: String,
    val finalResolvedUrl: String
)

object HttpMediaProber {

    fun probeDirectMediaUrl(
        urlString: String,
        dnsLookup: DnsLookup = SystemDnsLookup
    ): AppResult<HttpProbeResult> {
        // 1. Initial HEAD probe
        val headResult = SafeHttpTransport.openSafeConnection(
            initialUrl = urlString,
            method = "HEAD",
            dnsLookup = dnsLookup
        )

        when (headResult) {
            is AppResult.Success -> {
                val response = headResult.data
                try {
                    val code = response.responseCode
                    if (code in 200..299) {
                        val conn = response.connection
                        val contentLength = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                        val contentType = conn.contentType
                        val acceptRanges = conn.getHeaderField("Accept-Ranges")
                        val isRangeSupported = acceptRanges?.equals("bytes", ignoreCase = true) == true
                        val contentDisposition = conn.getHeaderField("Content-Disposition")
                        val suggestedFileName = extractFileName(response.finalUrl, contentDisposition, contentType)

                        return AppResult.Success(
                            HttpProbeResult(
                                contentLength = contentLength,
                                contentType = contentType,
                                isRangeSupported = isRangeSupported,
                                suggestedFileName = suggestedFileName,
                                finalResolvedUrl = response.finalUrl
                            )
                        )
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

        // 2. Bounded GET fallback with Range: bytes=0-4095 if HEAD was rejected or unsupported
        val getResult = SafeHttpTransport.openSafeConnection(
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

                    val conn = response.connection
                    var contentLength = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                    val contentRange = conn.getHeaderField("Content-Range")
                    val isRangeSupported = code == 206 || contentRange != null

                    // If Content-Range is present (e.g. "bytes 0-4095/10485760"), extract total length
                    if (!contentRange.isNullOrBlank()) {
                        val totalStr = contentRange.substringAfterLast('/')
                        val parsedTotal = totalStr.toLongOrNull()
                        if (parsedTotal != null && parsedTotal > 0) {
                            contentLength = parsedTotal
                        }
                    }

                    val contentType = conn.contentType
                    val contentDisposition = conn.getHeaderField("Content-Disposition")
                    val suggestedFileName = extractFileName(response.finalUrl, contentDisposition, contentType)

                    // Read bounded prefix and discard
                    runCatching {
                        conn.inputStream.use { stream ->
                            val buffer = ByteArray(4096)
                            stream.read(buffer)
                        }
                    }

                    AppResult.Success(
                        HttpProbeResult(
                            contentLength = contentLength,
                            contentType = contentType,
                            isRangeSupported = isRangeSupported,
                            suggestedFileName = suggestedFileName,
                            finalResolvedUrl = response.finalUrl
                        )
                    )
                } finally {
                    response.close()
                }
            }
            is AppResult.Error -> getResult
            AppResult.Loading -> AppResult.Loading
        }
    }

    fun extractFileName(urlString: String, contentDisposition: String?, contentType: String?): String {
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

        // 3. Extension fallback based on Content-Type
        val extension = when {
            contentType?.contains("video/mp4", ignoreCase = true) == true -> "mp4"
            contentType?.contains("video/webm", ignoreCase = true) == true -> "webm"
            contentType?.contains("video/x-matroska", ignoreCase = true) == true -> "mkv"
            contentType?.contains("audio/mp4", ignoreCase = true) == true -> "m4a"
            contentType?.contains("audio/mpeg", ignoreCase = true) == true -> "mp3"
            contentType?.contains("audio/ogg", ignoreCase = true) == true -> "ogg"
            contentType?.contains("audio/wav", ignoreCase = true) == true -> "wav"
            contentType?.contains("audio/flac", ignoreCase = true) == true -> "flac"
            else -> "mp4"
        }

        return "media_download_${System.currentTimeMillis()}.$extension"
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_").trim()
        val safe = if (cleaned.startsWith("..") || cleaned.isBlank()) "media_download" else cleaned
        return if (safe.length > 120) safe.take(120) else safe
    }
}
