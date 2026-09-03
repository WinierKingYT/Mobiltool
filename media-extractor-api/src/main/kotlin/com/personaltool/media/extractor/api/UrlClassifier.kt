package com.personaltool.media.extractor.api

import com.personaltool.core.model.media.MediaSource
import java.net.URI

sealed interface UrlValidationResult {
    data class Valid(
        val normalizedUrl: String,
        val platform: MediaSource,
        val host: String,
        val platformContentId: String? = null
    ) : UrlValidationResult

    data class Invalid(
        val reason: String,
        val isSsrfViolation: Boolean = false
    ) : UrlValidationResult
}

object UrlClassifier {

    private val YOUTUBE_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be"
    )

    private val INSTAGRAM_HOSTS = setOf(
        "instagram.com",
        "www.instagram.com"
    )

    private val X_TWITTER_HOSTS = setOf(
        "twitter.com",
        "www.twitter.com",
        "mobile.twitter.com",
        "x.com",
        "www.x.com"
    )

    private val YOUTUBE_REGEX = Regex(
        """^(https?://)?(www\.|m\.|music\.)?(youtube\.com|youtu\.be)/(watch\?v=|shorts/|embed/)?([a-zA-Z0-9_-]{11}).*""",
        RegexOption.IGNORE_CASE
    )

    private val INSTAGRAM_REGEX = Regex(
        """^(https?://)?(www\.)?instagram\.com/(p|reel|tv)/([a-zA-Z0-9_-]+).*""",
        RegexOption.IGNORE_CASE
    )

    private val X_TWITTER_REGEX = Regex(
        """^(https?://)?(www\.|mobile\.)?(twitter\.com|x\.com)/[a-zA-Z0-9_]+/status/([0-9]+).*""",
        RegexOption.IGNORE_CASE
    )

    fun validateAndNormalize(
        rawUrl: String,
        dnsLookup: DnsLookup = SystemDnsLookup
    ): UrlValidationResult {
        when (val netResult = NetworkSecurityPolicy.validateDestination(rawUrl, dnsLookup)) {
            is NetworkValidationResult.Blocked -> {
                return UrlValidationResult.Invalid(
                    reason = netResult.reason,
                    isSsrfViolation = netResult.isSsrfViolation
                )
            }
            is NetworkValidationResult.Valid -> {
                val canonicalHost = netResult.canonicalHost
                val platform = classifyHostAndUrl(canonicalHost, netResult.normalizedUrl)
                val contentId = extractPlatformId(netResult.normalizedUrl, platform)

                return UrlValidationResult.Valid(
                    normalizedUrl = netResult.normalizedUrl,
                    platform = platform,
                    host = canonicalHost,
                    platformContentId = contentId
                )
            }
        }
    }

    /**
     * Strict host boundary classifier.
     * Prevents attacker-controlled subdomains (e.g. youtube.com.attacker.example) from being classified as YouTube.
     */
    fun classifyHostAndUrl(host: String, url: String): MediaSource {
        val normalizedHost = host.lowercase()
        return when {
            normalizedHost in YOUTUBE_HOSTS || YOUTUBE_REGEX.matches(url) -> {
                if (normalizedHost in YOUTUBE_HOSTS) MediaSource.YOUTUBE else MediaSource.GENERIC_URL
            }
            normalizedHost in INSTAGRAM_HOSTS || INSTAGRAM_REGEX.matches(url) -> {
                if (normalizedHost in INSTAGRAM_HOSTS) MediaSource.INSTAGRAM else MediaSource.GENERIC_URL
            }
            normalizedHost in X_TWITTER_HOSTS || X_TWITTER_REGEX.matches(url) -> {
                if (normalizedHost in X_TWITTER_HOSTS) MediaSource.X_TWITTER else MediaSource.GENERIC_URL
            }
            else -> MediaSource.GENERIC_URL
        }
    }

    fun extractPlatformId(url: String, platform: MediaSource): String? {
        return when (platform) {
            MediaSource.YOUTUBE -> {
                val match = YOUTUBE_REGEX.find(url)
                match?.groupValues?.getOrNull(5)
            }
            MediaSource.INSTAGRAM -> {
                val match = INSTAGRAM_REGEX.find(url)
                match?.groupValues?.getOrNull(4)
            }
            MediaSource.X_TWITTER -> {
                val match = X_TWITTER_REGEX.find(url)
                match?.groupValues?.getOrNull(4)
            }
            else -> null
        }
    }
}
