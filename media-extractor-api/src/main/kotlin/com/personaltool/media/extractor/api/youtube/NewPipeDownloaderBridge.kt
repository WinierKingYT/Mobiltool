package com.personaltool.media.extractor.api.youtube

import com.personaltool.media.extractor.api.DnsLookup
import com.personaltool.media.extractor.api.NetworkSecurityPolicy
import com.personaltool.media.extractor.api.NetworkValidationResult
import com.personaltool.media.extractor.api.SystemDnsLookup
import com.personaltool.media.extractor.api.ValidatedDns
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Hardened NewPipe Downloader Bridge (P2-YT-FINAL-01, P2-YT-FINAL-01B).
 *
 * Enforces strict destination security on ALL NewPipe-originated HTTP requests:
 * 1. Destination pre-validation against SSRF / private IP policies via NetworkSecurityPolicy.
 * 2. Strict DNS binding via ValidatedDns to the pre-validated IP address set (mitigating DNS TOCTOU / rebinding).
 * 3. Manual hop-by-hop redirect re-validation with cycle detection and HTTPS -> HTTP downgrade prevention.
 * 4. Standard TLS certificate and hostname verification (NO trust-all, NO hostname bypasses).
 * 5. Full support for GET, HEAD, and POST methods with preserved headers (303 -> GET, 307/308 -> preserve method).
 */
class NewPipeDownloaderBridge(
    val dnsLookup: DnsLookup = SystemDnsLookup,
    val connectTimeoutMs: Long = 15000L,
    val readTimeoutMs: Long = 15000L,
    val maxRedirects: Int = 5,
    private val clientFactory: (ValidatedDns) -> OkHttpClient = { validatedDns ->
        OkHttpClient.Builder()
            .dns(validatedDns)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }
) : Downloader() {

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    override fun execute(request: Request): Response {
        val initialUrl = request.url()
        val initialMethod = request.httpMethod()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        var currentUrl = initialUrl
        var currentMethod = initialMethod
        var redirectCount = 0
        val visitedUrls = mutableSetOf<String>()

        while (true) {
            // 1. Destination validation and approved IP resolution
            val netResult = NetworkSecurityPolicy.validateDestination(currentUrl, dnsLookup)
            if (netResult is NetworkValidationResult.Blocked) {
                throw IOException("Network policy blocked request to $currentUrl: ${netResult.reason}")
            }

            val validDest = netResult as NetworkValidationResult.Valid
            val approvedIps = validDest.resolvedIps

            if (!visitedUrls.add(currentUrl)) {
                throw IOException("Redirect cycle detected at $currentUrl")
            }

            // 2. Build OkHttpClient bound to approved DNS resolution and standard TLS
            val client = clientFactory(ValidatedDns(approvedIps))

            val reqBuilder = okhttp3.Request.Builder().url(currentUrl)
            var hasUserAgent = false
            var hasAcceptLang = false

            headers?.forEach { (name, values) ->
                if (name.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
                if (name.equals("Accept-Language", ignoreCase = true)) hasAcceptLang = true
                values.forEach { v -> reqBuilder.addHeader(name, v) }
            }

            if (!hasUserAgent) {
                reqBuilder.header("User-Agent", DEFAULT_USER_AGENT)
            }
            if (!hasAcceptLang) {
                reqBuilder.header("Accept-Language", "en-US,en;q=0.9")
            }

            // Method handling
            if (currentMethod.equals("POST", ignoreCase = true)) {
                val body = dataToSend?.toRequestBody() ?: ByteArray(0).toRequestBody()
                reqBuilder.post(body)
            } else if (currentMethod.equals("HEAD", ignoreCase = true)) {
                reqBuilder.head()
            } else {
                reqBuilder.get()
            }

            val okResponse = try {
                client.newCall(reqBuilder.build()).execute()
            } catch (e: Exception) {
                throw IOException("Failed connecting to $currentUrl: ${e.message}", e)
            }

            val responseCode = okResponse.code

            // 3. Manual Hop-by-Hop Redirect Handling
            if (responseCode in listOf(301, 302, 303, 307, 308)) {
                val location = okResponse.header("Location")
                okResponse.close()

                if (location.isNullOrBlank()) {
                    throw IOException("HTTP $responseCode redirect from $currentUrl missing Location header")
                }

                redirectCount++
                if (redirectCount > maxRedirects) {
                    throw IOException("Redirect limit ($maxRedirects) exceeded")
                }

                val resolvedUri = try {
                    val currentUri = URI(currentUrl)
                    currentUri.resolve(location)
                } catch (e: Exception) {
                    throw IOException("Malformed redirect Location header: $location", e)
                }

                val nextUrl = resolvedUri.toString()

                // Protocol downgrade prevention
                val currentScheme = URI(currentUrl).scheme?.lowercase()
                val nextScheme = resolvedUri.scheme?.lowercase()
                if (currentScheme == "https" && nextScheme == "http") {
                    throw IOException("Insecure protocol downgrade from HTTPS to HTTP blocked on redirect to $nextUrl")
                }

                // Method preservation rules across HTTP redirect codes
                when (responseCode) {
                    303 -> currentMethod = "GET" // 303 See Other explicitly changes method to GET
                    307, 308 -> { /* 307 Temporary Redirect and 308 Permanent Redirect MUST preserve request method */ }
                    301, 302 -> {
                        // For 301/302, standard HTTP behavior changes POST to GET
                        if (currentMethod.equals("POST", ignoreCase = true)) {
                            currentMethod = "GET"
                        }
                    }
                }

                currentUrl = nextUrl
                continue
            }

            val responseBody = okResponse.body?.string() ?: ""
            val responseHeaders = mutableMapOf<String, List<String>>()
            okResponse.headers.names().forEach { name ->
                responseHeaders[name] = okResponse.headers.values(name)
            }

            return Response(
                okResponse.code,
                okResponse.message,
                responseHeaders,
                responseBody,
                okResponse.request.url.toString()
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NewPipeDownloaderBridge) return false
        return dnsLookup == other.dnsLookup &&
                connectTimeoutMs == other.connectTimeoutMs &&
                readTimeoutMs == other.readTimeoutMs &&
                maxRedirects == other.maxRedirects
    }

    override fun hashCode(): Int {
        var result = dnsLookup.hashCode()
        result = 31 * result + connectTimeoutMs.hashCode()
        result = 31 * result + readTimeoutMs.hashCode()
        result = 31 * result + maxRedirects
        return result
    }
}
