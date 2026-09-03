package com.personaltool.media.extractor.api

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.net.InetAddress

class NetworkSecurityPolicyTest {

    @Test
    fun isRestrictedAddress_identifiesAllIpv4PrivateAndRestrictedRanges() {
        val restrictedIpv4s = listOf(
            "0.0.0.0",
            "10.0.0.1",
            "10.254.254.254",
            "100.64.0.1",        // CGNAT start
            "100.100.50.25",     // CGNAT middle
            "100.127.255.255",   // CGNAT end
            "127.0.0.1",        // Loopback
            "127.12.34.56",      // Loopback full /8
            "169.254.169.254",   // Cloud metadata / Link-local
            "172.16.0.1",        // RFC 1918 /12 start
            "172.24.10.20",      // RFC 1918 /12 middle
            "172.31.255.255",    // RFC 1918 /12 end
            "192.0.2.1",         // TEST-NET-1
            "192.168.0.1",       // RFC 1918 /16
            "192.168.254.254",
            "198.18.0.1",        // Benchmark test range
            "198.51.100.1",      // TEST-NET-2
            "203.0.113.1",       // TEST-NET-3
            "224.0.0.1",         // Multicast
            "239.255.255.250",   // Multicast
            "240.0.0.1",         // Reserved
            "255.255.255.255"    // Broadcast
        )

        for (ipStr in restrictedIpv4s) {
            val addr = InetAddress.getByName(ipStr)
            val isRestricted = NetworkSecurityPolicy.isRestrictedAddress(addr)
            assertWithMessage("IP $ipStr should be restricted").that(isRestricted).isTrue()
        }
    }

    @Test
    fun isRestrictedAddress_allowsPublicIpv4Addresses() {
        val publicIpv4s = listOf(
            "8.8.8.8",
            "1.1.1.1",
            "93.184.216.34",
            "142.250.190.46",
            "151.101.1.140"
        )

        for (ipStr in publicIpv4s) {
            val addr = InetAddress.getByName(ipStr)
            val isRestricted = NetworkSecurityPolicy.isRestrictedAddress(addr)
            assertWithMessage("Public IP $ipStr should NOT be restricted").that(isRestricted).isFalse()
        }
    }

    @Test
    fun isRestrictedAddress_identifiesIpv6RestrictedRanges() {
        val restrictedIpv6s = listOf(
            "::1",                      // Loopback
            "::",                       // Unspecified
            "fe80::1",                  // Link-local
            "fe80::200:5aee:feaa:20a2", // Link-local
            "fc00::1",                  // Unique local (ULA)
            "fd12:3456:789a::1",        // Unique local (ULA)
            "2001:db8::1",              // Documentation
            "ff02::1",                  // Multicast
            "::ffff:127.0.0.1",         // IPv4-mapped loopback
            "::ffff:10.0.0.1",          // IPv4-mapped private
            "::ffff:192.168.1.1"        // IPv4-mapped private
        )

        for (ipStr in restrictedIpv6s) {
            val addr = InetAddress.getByName(ipStr)
            val isRestricted = NetworkSecurityPolicy.isRestrictedAddress(addr)
            assertWithMessage("IPv6 $ipStr should be restricted").that(isRestricted).isTrue()
        }
    }

    @Test
    fun validateDestination_rejectsEmbeddedUserInfo() {
        val result = NetworkSecurityPolicy.validateDestination("https://user:password@example.com/video.mp4")
        assertThat(result).isInstanceOf(NetworkValidationResult.Blocked::class.java)
        val blocked = result as NetworkValidationResult.Blocked
        assertThat(blocked.reason).contains("credentials")
        assertThat(blocked.isSsrfViolation).isTrue()
    }

    @Test
    fun validateDestination_rejectsNonHttpSchemes() {
        val invalidUrls = listOf(
            "file:///sdcard/video.mp4",
            "ftp://files.example.com/audio.mp3",
            "gopher://example.com/test",
            "javascript:alert(1)",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg=="
        )

        for (url in invalidUrls) {
            val result = NetworkSecurityPolicy.validateDestination(url)
            assertThat(result).isInstanceOf(NetworkValidationResult.Blocked::class.java)
        }
    }

    @Test
    fun validateDestination_withDnsResolvingToPrivateIp_isBlocked() {
        val mockDns = DnsLookup { hostname ->
            if (hostname == "evil-internal.example.com") {
                listOf(InetAddress.getByName("10.0.0.5"))
            } else {
                listOf(InetAddress.getByName("93.184.216.34"))
            }
        }

        val result = NetworkSecurityPolicy.validateDestination(
            "https://evil-internal.example.com/video.mp4",
            dnsLookup = mockDns
        )

        assertThat(result).isInstanceOf(NetworkValidationResult.Blocked::class.java)
        val blocked = result as NetworkValidationResult.Blocked
        assertThat(blocked.isSsrfViolation).isTrue()
        assertThat(blocked.reason).contains("10.0.0.5")
    }

    @Test
    fun validateDestination_withPublicHostAndDns_isValid() {
        val mockDns = DnsLookup { listOf(InetAddress.getByName("93.184.216.34")) }

        val result = NetworkSecurityPolicy.validateDestination(
            "https://cdn.example.com/media/sample.mp4",
            dnsLookup = mockDns
        )

        assertThat(result).isInstanceOf(NetworkValidationResult.Valid::class.java)
        val valid = result as NetworkValidationResult.Valid
        assertThat(valid.canonicalHost).isEqualTo("cdn.example.com")
        assertThat(valid.resolvedIps).hasSize(1)
    }
}
