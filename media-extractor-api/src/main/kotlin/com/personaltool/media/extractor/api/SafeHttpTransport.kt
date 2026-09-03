package com.personaltool.media.extractor.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

data class SafeHttpResponse(
    val response: Response?,
    val responseBodyStream: InputStream,
    val contentLength: Long,
    val contentType: String?,
    val finalUrl: String,
    val responseCode: Int,
    val redirectCount: Int
) : Closeable {
    override fun close() {
        runCatching { responseBodyStream.close() }
        runCatching { response?.close() }
    }
}

interface SafeHttpTransportEngine {
    fun openSafeConnection(
        initialUrl: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        maxRedirects: Int = SafeHttpTransport.DEFAULT_MAX_REDIRECTS,
        connectTimeoutMs: Long = SafeHttpTransport.DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Long = SafeHttpTransport.DEFAULT_READ_TIMEOUT_MS,
        dnsLookup: DnsLookup = SystemDnsLookup
    ): AppResult<SafeHttpResponse>
}

object SafeHttpTransport : SafeHttpTransportEngine {

    const val DEFAULT_MAX_REDIRECTS = 5
    const val DEFAULT_CONNECT_TIMEOUT_MS = 10000L
    const val DEFAULT_READ_TIMEOUT_MS = 15000L

    /**
     * Executes an HTTP request with:
     * 1. Pre-connection DNS validation against SSRF / private IP policies.
     * 2. Strict DNS binding to the pre-validated IP address set (eliminating DNS TOCTOU / Rebinding attacks).
     * 3. Manual hop-by-hop redirect re-validation with cycle detection and downgrade protection.
     */
    override fun openSafeConnection(
        initialUrl: String,
        method: String,
        headers: Map<String, String>,
        maxRedirects: Int,
        connectTimeoutMs: Long,
        readTimeoutMs: Long,
        dnsLookup: DnsLookup
    ): AppResult<SafeHttpResponse> {
        var currentUrl = initialUrl
        var redirectCount = 0
        val visitedUrls = mutableSetOf<String>()

        while (true) {
            // 1. Validate destination and resolve approved IP addresses
            val netResult = NetworkSecurityPolicy.validateDestination(currentUrl, dnsLookup)
            if (netResult is NetworkValidationResult.Blocked) {
                return AppResult.Error(
                    message = "Network policy blocked request to $currentUrl: ${netResult.reason}",
                    code = if (netResult.isSsrfViolation) ErrorCode.SECURITY_VIOLATION else ErrorCode.VALIDATION_ERROR
                )
            }

            val validDest = netResult as NetworkValidationResult.Valid
            val approvedIps = validDest.resolvedIps

            if (!visitedUrls.add(currentUrl)) {
                return AppResult.Error(
                    message = "Redirect cycle detected at $currentUrl",
                    code = ErrorCode.NETWORK_ERROR
                )
            }

            // 2. Build OkHttpClient bound to approved DNS resolution (DNS TOCTOU mitigation)
            val client = OkHttpClient.Builder()
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        // Strict binding: Only return approved, pre-validated IPs for this host
                        return approvedIps
                    }
                })
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .build()

            val requestBuilder = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", "Mobiltool/1.0 (Linux; Android)")

            headers.forEach { (k, v) -> requestBuilder.header(k, v) }

            if (method.equals("HEAD", ignoreCase = true)) {
                requestBuilder.head()
            } else {
                requestBuilder.get()
            }

            val response = try {
                client.newCall(requestBuilder.build()).execute()
            } catch (e: Exception) {
                return AppResult.Error(
                    message = "Failed connecting to $currentUrl: ${e.message}",
                    code = ErrorCode.NETWORK_ERROR
                )
            }

            val responseCode = response.code

            // 3. Manual Hop-by-Hop Redirect Handling
            if (responseCode in listOf(301, 302, 303, 307, 308)) {
                val location = response.header("Location")
                response.close()

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

                // Protocol downgrade prevention
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

            val body = response.body
            val stream = body?.byteStream() ?: "".byteInputStream()
            val contentLength = body?.contentLength() ?: -1L
            val contentType = response.header("Content-Type")

            return AppResult.Success(
                SafeHttpResponse(
                    response = response,
                    responseBodyStream = stream,
                    contentLength = contentLength,
                    contentType = contentType,
                    finalUrl = currentUrl,
                    responseCode = responseCode,
                    redirectCount = redirectCount
                )
            )
        }
    }
}
