package com.personaltool.media.extractor.api

import com.personaltool.core.model.media.MediaSource
import java.net.URI

object UrlClassifier {

    private val YOUTUBE_REGEX = Regex(
        """^(https?://)?(www\.)?(youtube\.com|youtu\.be)/(watch\?v=|shorts/|embed/)?([a-zA-Z0-9_-]{11}).*""",
        RegexOption.IGNORE_CASE
    )

    private val INSTAGRAM_REGEX = Regex(
        """^(https?://)?(www\.)?instagram\.com/(p|reel|tv)/([a-zA-Z0-9_-]+).*""",
        RegexOption.IGNORE_CASE
    )

    private val X_TWITTER_REGEX = Regex(
        """^(https?://)?(www\.)?(twitter\.com|x\.com)/[a-zA-Z0-9_]+/status/([0-9]+).*""",
        RegexOption.IGNORE_CASE
    )

    fun validateAndNormalize(rawUrl: String): UrlValidationResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return UrlValidationResult.Invalid("URL cannot be empty")
        }

        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return UrlValidationResult.Invalid("Malformed URL syntax")

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return UrlValidationResult.Invalid("Only HTTP and HTTPS URLs are supported (rejected: $scheme)")
        }

        val host = uri.host?.lowercase() ?: return UrlValidationResult.Invalid("Missing host in URL")

        // SSRF & Localhost Protection (Hard Invariant)
        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host.startsWith("192.168.") || host.startsWith("10.")) {
            return UrlValidationResult.Invalid("Local and private network addresses are prohibited")
        }

        val platform = classify(trimmed, host)
        return UrlValidationResult.Valid(
            normalizedUrl = trimmed,
            platform = platform,
            host = host
        )
    }

    private fun classify(url: String, host: String): MediaSource {
        return when {
            host.contains("youtube.com") || host.contains("youtu.be") || YOUTUBE_REGEX.matches(url) -> MediaSource.YOUTUBE
            host.contains("instagram.com") || INSTAGRAM_REGEX.matches(url) -> MediaSource.INSTAGRAM
            host.contains("twitter.com") || host.contains("x.com") || X_TWITTER_REGEX.matches(url) -> MediaSource.X_TWITTER
            else -> MediaSource.GENERIC_URL
        }
    }
}

sealed interface UrlValidationResult {
    data class Valid(
        val normalizedUrl: String,
        val platform: MediaSource,
        val host: String
    ) : UrlValidationResult

    data class Invalid(val reason: String) : UrlValidationResult
}
