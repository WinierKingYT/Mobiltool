package com.personaltool.media.extractor.api

import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

sealed interface NetworkValidationResult {
    data class Valid(
        val normalizedUrl: String,
        val canonicalHost: String,
        val resolvedIps: List<InetAddress>
    ) : NetworkValidationResult

    data class Blocked(
        val reason: String,
        val isSsrfViolation: Boolean = false
    ) : NetworkValidationResult
}

fun interface DnsLookup {
    fun lookup(hostname: String): List<InetAddress>
}

object SystemDnsLookup : DnsLookup {
    override fun lookup(hostname: String): List<InetAddress> {
        return InetAddress.getAllByName(hostname).toList()
    }
}

object NetworkSecurityPolicy {

    /**
     * Checks whether an IP address is a private, loopback, link-local, multicast,
     * CGNAT, documentation, or reserved/unspecified address (SSRF defense).
     */
    fun isRestrictedAddress(address: InetAddress): Boolean {
        // 1. Standard Java checks
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }

        val rawBytes = address.address

        // 2. IPv4 specific ranges
        if (address is Inet4Address || rawBytes.size == 4) {
            val b0 = rawBytes[0].toInt() and 0xFF
            val b1 = rawBytes[1].toInt() and 0xFF

            // 0.0.0.0/8 (Current network)
            if (b0 == 0) return true
            // 10.0.0.0/8 (RFC 1918)
            if (b0 == 10) return true
            // 100.64.0.0/10 (Carrier-grade NAT RFC 6598: 100.64.0.0 - 100.127.255.255)
            if (b0 == 100 && b1 in 64..127) return true
            // 127.0.0.0/8 (Loopback)
            if (b0 == 127) return true
            // 169.254.0.0/16 (Link-local RFC 3927)
            if (b0 == 169 && b1 == 254) return true
            // 172.16.0.0/12 (RFC 1918: 172.16.0.0 - 172.31.255.255)
            if (b0 == 172 && b1 in 16..31) return true
            // 192.0.0.0/24 (IETF Protocol Assignments)
            if (b0 == 192 && b1 == 0 && (rawBytes[2].toInt() and 0xFF) == 0) return true
            // 192.0.2.0/24 (TEST-NET-1)
            if (b0 == 192 && b1 == 0 && (rawBytes[2].toInt() and 0xFF) == 2) return true
            // 192.168.0.0/16 (RFC 1918)
            if (b0 == 192 && b1 == 168) return true
            // 198.18.0.0/15 (Network benchmark tests: 198.18.0.0 - 198.19.255.255)
            if (b0 == 198 && b1 in 18..19) return true
            // 198.51.100.0/24 (TEST-NET-2)
            if (b0 == 198 && b1 == 51 && (rawBytes[2].toInt() and 0xFF) == 100) return true
            // 203.0.113.0/24 (TEST-NET-3)
            if (b0 == 203 && b1 == 0 && (rawBytes[2].toInt() and 0xFF) == 113) return true
            // 224.0.0.0/4 (Multicast: 224..239)
            if (b0 in 224..239) return true
            // 240.0.0.0/4 (Reserved for future use: 240..255)
            if (b0 >= 240) return true
        }

        // 3. IPv6 specific ranges
        if (address is Inet6Address || rawBytes.size == 16) {
            val b0 = rawBytes[0].toInt() and 0xFF
            val b1 = rawBytes[1].toInt() and 0xFF

            // ::/128 (Unspecified) and ::1/128 (Loopback)
            if (rawBytes.take(15).all { it == 0.toByte() }) {
                val last = rawBytes[15].toInt() and 0xFF
                if (last == 0 || last == 1) return true
            }

            // IPv4-mapped IPv6: ::ffff:0:0/96
            val isIpv4Mapped = rawBytes.take(10).all { it == 0.toByte() } &&
                    rawBytes[10] == 0xFF.toByte() &&
                    rawBytes[11] == 0xFF.toByte()

            if (isIpv4Mapped) {
                val ipv4Bytes = rawBytes.sliceArray(12..15)
                val mappedIpv4 = InetAddress.getByAddress(ipv4Bytes)
                return isRestrictedAddress(mappedIpv4)
            }

            // IPv4-compatible IPv6 (deprecated): ::<ipv4>
            val isIpv4Compatible = rawBytes.take(12).all { it == 0.toByte() } &&
                    !(rawBytes[12] == 0.toByte() && rawBytes[13] == 0.toByte() && rawBytes[14] == 0.toByte() && (rawBytes[15].toInt() and 0xFF) in 0..1)

            if (isIpv4Compatible) {
                val ipv4Bytes = rawBytes.sliceArray(12..15)
                val compatIpv4 = InetAddress.getByAddress(ipv4Bytes)
                return isRestrictedAddress(compatIpv4)
            }

            // Unique Local Address (ULA): fc00::/7 (fc00..fdff)
            if ((b0 and 0xFE) == 0xFC) return true

            // Link-local: fe80::/10 (fe80..febf)
            if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return true

            // Documentation: 2001:db8::/32
            if (b0 == 0x20 && b1 == 0x01 && (rawBytes[2].toInt() and 0xFF) == 0x0D && (rawBytes[3].toInt() and 0xFF) == 0xB8) return true

            // Multicast: ff00::/8
            if (b0 == 0xFF) return true
        }

        return false
    }

    /**
     * Validates destination URL against SSRF and network security policies.
     */
    fun validateDestination(
        urlString: String,
        dnsLookup: DnsLookup = SystemDnsLookup
    ): NetworkValidationResult {
        val trimmed = urlString.trim()
        if (trimmed.isBlank()) {
            return NetworkValidationResult.Blocked("URL cannot be empty")
        }

        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return NetworkValidationResult.Blocked("Malformed URI syntax")

        // 1. Strict scheme policy: HTTP / HTTPS only
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return NetworkValidationResult.Blocked("Only HTTP and HTTPS schemes are permitted (rejected: $scheme)")
        }

        // 2. Reject credentials / userinfo in URL
        if (!uri.userInfo.isNullOrBlank()) {
            return NetworkValidationResult.Blocked("Embedded user credentials in URL are prohibited for security", isSsrfViolation = true)
        }

        val rawHost = uri.host ?: return NetworkValidationResult.Blocked("URL is missing a valid host")
        if (rawHost.isBlank()) {
            return NetworkValidationResult.Blocked("Host cannot be blank")
        }

        // 3. IDN / Punycode normalization
        val canonicalHost = runCatching { IDN.toASCII(rawHost).lowercase() }.getOrNull()
            ?: return NetworkValidationResult.Blocked("Invalid internationalized domain name (IDN)")

        // 4. Quick reject of obvious local strings / IP literals before DNS lookup
        if (canonicalHost == "localhost" ||
            canonicalHost.endsWith(".localhost") ||
            canonicalHost.endsWith(".local") ||
            canonicalHost.endsWith(".internal")
        ) {
            return NetworkValidationResult.Blocked("Local / internal hostname targets are prohibited", isSsrfViolation = true)
        }

        // If canonicalHost is an IP literal, validate directly
        val rawIpLiteral = runCatching {
            if (canonicalHost.matches(Regex("""^\d+\.\d+\.\d+\.\d+$""")) || canonicalHost.contains(':')) {
                InetAddress.getByName(canonicalHost)
            } else null
        }.getOrNull()

        if (rawIpLiteral != null) {
            if (isRestrictedAddress(rawIpLiteral)) {
                return NetworkValidationResult.Blocked(
                    reason = "Target host $canonicalHost is a prohibited private/local IP literal",
                    isSsrfViolation = true
                )
            }
            return NetworkValidationResult.Valid(
                normalizedUrl = trimmed,
                canonicalHost = canonicalHost,
                resolvedIps = listOf(rawIpLiteral)
            )
        }

        // 5. DNS Resolution and IP policy verification
        val resolvedIps = try {
            dnsLookup.lookup(canonicalHost)
        } catch (e: Exception) {
            return NetworkValidationResult.Blocked("DNS resolution failed for host $canonicalHost: ${e.message}")
        }

        if (resolvedIps.isEmpty()) {
            return NetworkValidationResult.Blocked("Host $canonicalHost resolved to zero IP addresses")
        }

        // If ANY resolved IP is in a restricted range, fail closed
        for (ip in resolvedIps) {
            if (isRestrictedAddress(ip)) {
                return NetworkValidationResult.Blocked(
                    reason = "Host $canonicalHost resolved to prohibited private/local IP: ${ip.hostAddress}",
                    isSsrfViolation = true
                )
            }
        }

        return NetworkValidationResult.Valid(
            normalizedUrl = trimmed,
            canonicalHost = canonicalHost,
            resolvedIps = resolvedIps
        )
    }
}
