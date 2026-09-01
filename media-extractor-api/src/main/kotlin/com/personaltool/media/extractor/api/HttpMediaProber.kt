package com.personaltool.media.extractor.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class HttpProbeResult(
    val contentLength: Long,
    val contentType: String?,
    val isRangeSupported: Boolean,
    val suggestedFileName: String,
    val finalResolvedUrl: String
)

object HttpMediaProber {

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 10000

    fun probeDirectMediaUrl(urlString: String): AppResult<HttpProbeResult> {
        val validation = UrlClassifier.validateAndNormalize(urlString)
        if (validation is UrlValidationResult.Invalid) {
            return AppResult.Error(validation.reason, code = ErrorCode.VALIDATION_ERROR)
        }

        return try {
            val url = URI(urlString).toURL()
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mobiltool/1.0 (Linux; Android)")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..399) {
                return AppResult.Error(
                    message = "Server returned HTTP $responseCode (${connection.responseMessage})",
                    code = ErrorCode.NETWORK_ERROR
                )
            }

            val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            val contentType = connection.contentType
            val acceptRanges = connection.getHeaderField("Accept-Ranges")
            val isRangeSupported = acceptRanges?.equals("bytes", ignoreCase = true) == true

            val contentDisposition = connection.getHeaderField("Content-Disposition")
            val suggestedFileName = extractFileName(urlString, contentDisposition, contentType)
            val finalUrl = connection.url.toString()

            connection.disconnect()

            AppResult.Success(
                HttpProbeResult(
                    contentLength = contentLength,
                    contentType = contentType,
                    isRangeSupported = isRangeSupported,
                    suggestedFileName = suggestedFileName,
                    finalResolvedUrl = finalUrl
                )
            )
        } catch (e: Exception) {
            AppResult.Error(
                message = "Network probe failed: ${e.message}",
                code = ErrorCode.NETWORK_ERROR
            )
        }
    }

    fun extractFileName(urlString: String, contentDisposition: String?, contentType: String?): String {
        // 1. Try Content-Disposition header
        if (!contentDisposition.isNullOrBlank()) {
            val regex = Regex("""filename\*?=['"]?(?:UTF-8'')?([^'";\r\n]+)['"]?""", RegexOption.IGNORE_CASE)
            val match = regex.find(contentDisposition)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.isNotBlank()) return sanitizeFileName(candidate)
            }
        }

        // 2. Try URL path
        val path = runCatching { URI(urlString).path }.getOrNull()
        if (!path.isNullOrBlank()) {
            val lastSegment = path.substringAfterLast('/').substringBefore('?')
            if (lastSegment.isNotBlank() && lastSegment.contains('.')) {
                return sanitizeFileName(lastSegment)
            }
        }

        // 3. Fallback based on content-type
        val extension = when {
            contentType?.contains("video/mp4", ignoreCase = true) == true -> "mp4"
            contentType?.contains("audio/mp4", ignoreCase = true) == true -> "m4a"
            contentType?.contains("audio/mpeg", ignoreCase = true) == true -> "mp3"
            contentType?.contains("video/webm", ignoreCase = true) == true -> "webm"
            else -> "bin"
        }

        return "media_download_${System.currentTimeMillis()}.$extension"
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    }
}
