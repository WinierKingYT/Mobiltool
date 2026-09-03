package com.personaltool.media.extractor.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URI

data class SafeHttpResponse(
    val connection: HttpURLConnection,
    val finalUrl: String,
    val responseCode: Int,
    val redirectCount: Int
) : Closeable {
    override fun close() {
        runCatching { connection.disconnect() }
    }
}

object SafeHttpTransport {

    const val DEFAULT_MAX_REDIRECTS = 5
    const val DEFAULT_CONNECT_TIMEOUT_MS = 10000
    const val DEFAULT_READ_TIMEOUT_MS = 15000

    /**
     * Executes an HTTP request while manually inspecting and re-validating every redirect hop.
     * Prevents SSRF redirection attacks, redirect cycles, and HTTPS -> HTTP downgrades.
     */
    fun openSafeConnection(
        initialUrl: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
        dnsLookup: DnsLookup = SystemDnsLookup
    ): AppResult<SafeHttpResponse> {
        var currentUrl = initialUrl
        var redirectCount = 0
        val visitedUrls = mutableSetOf<String>()

        while (true) {
            // 1. Validate destination on EVERY hop (including initial and all redirects)
            val netResult = NetworkSecurityPolicy.validateDestination(currentUrl, dnsLookup)
            if (netResult is NetworkValidationResult.Blocked) {
                return AppResult.Error(
                    message = "Network policy blocked request to $currentUrl: ${netResult.reason}",
                    code = if (netResult.isSsrfViolation) ErrorCode.SECURITY_VIOLATION else ErrorCode.VALIDATION_ERROR
                )
            }

            if (!visitedUrls.add(currentUrl)) {
                return AppResult.Error(
                    message = "Redirect cycle detected at $currentUrl",
                    code = ErrorCode.NETWORK_ERROR
                )
            }

            val connection: HttpURLConnection
            try {
                val uri = URI(currentUrl)
                connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    instanceFollowRedirects = false // Manual inspection on every hop!
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    setRequestProperty("User-Agent", "Mobiltool/1.0 (Linux; Android)")
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }
            } catch (e: Exception) {
                return AppResult.Error(
                    message = "Failed to establish connection to $currentUrl: ${e.message}",
                    code = ErrorCode.NETWORK_ERROR
                )
            }

            val responseCode = try {
                connection.responseCode
            } catch (e: Exception) {
                connection.disconnect()
                return AppResult.Error(
                    message = "Network error reading response from $currentUrl: ${e.message}",
                    code = ErrorCode.NETWORK_ERROR
                )
            }

            // 2. Handle HTTP Redirects (301, 302, 303, 307, 308)
            if (responseCode in listOf(
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307, // Temporary Redirect
                    308  // Permanent Redirect
                )
            ) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()

                if (location.isNullOrBlank()) {
                    return AppResult.Error(
                        message = "HTTP $responseCode redirect from $currentUrl missing Location header",
                        code = ErrorCode.NETWORK_ERROR
                    )
                }

                redirectCount++
                if (redirectCount > maxRedirects) {
                    return AppResult.Error(
                        message = "Redirect limit ($maxRedirects) exceeded",
                        code = ErrorCode.NETWORK_ERROR
                    )
                }

                // Resolve relative location URLs against the current URL
                val resolvedUri = try {
                    val currentUri = URI(currentUrl)
                    currentUri.resolve(location)
                } catch (e: Exception) {
                    return AppResult.Error(
                        message = "Malformed redirect Location header: $location",
                        code = ErrorCode.VALIDATION_ERROR
                    )
                }

                val nextUrl = resolvedUri.toString()

                // Protocol downgrade check: Disallow HTTPS -> HTTP downgrade
                val currentScheme = URI(currentUrl).scheme?.lowercase()
                val nextScheme = resolvedUri.scheme?.lowercase()
                if (currentScheme == "https" && nextScheme == "http") {
                    return AppResult.Error(
                        message = "Insecure protocol downgrade from HTTPS to HTTP blocked on redirect to $nextUrl",
                        code = ErrorCode.SECURITY_VIOLATION
                    )
                }

                currentUrl = nextUrl
                continue
            }

            return AppResult.Success(
                SafeHttpResponse(
                    connection = connection,
                    finalUrl = currentUrl,
                    responseCode = responseCode,
                    redirectCount = redirectCount
                )
            )
        }
    }
}
