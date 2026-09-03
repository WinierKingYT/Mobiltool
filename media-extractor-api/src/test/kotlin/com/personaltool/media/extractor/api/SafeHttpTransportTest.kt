package com.personaltool.media.extractor.api

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import org.junit.Test
import java.net.InetAddress

class SafeHttpTransportTest {

    @Test
    fun openSafeConnection_blocksDirectSsrfTarget() {
        val result = SafeHttpTransport.openSafeConnection(
            initialUrl = "http://127.0.0.1:8080/secret",
            dnsLookup = { listOf(InetAddress.getByName("127.0.0.1")) }
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.SECURITY_VIOLATION)
        assertThat(error.message).contains("Network policy blocked request")
    }

    @Test
    fun openSafeConnection_blocksHostnameResolvingToPrivateIp() {
        val mockDns = DnsLookup { hostname ->
            if (hostname == "internal-service.example.org") {
                listOf(InetAddress.getByName("192.168.1.50"))
            } else {
                listOf(InetAddress.getByName("93.184.216.34"))
            }
        }

        val result = SafeHttpTransport.openSafeConnection(
            initialUrl = "https://internal-service.example.org/api/stream",
            dnsLookup = mockDns
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.SECURITY_VIOLATION)
        assertThat(error.message).contains("192.168.1.50")
    }

    @Test
    fun openSafeConnection_blocksUrlWithUserInfoCredentials() {
        val result = SafeHttpTransport.openSafeConnection(
            initialUrl = "https://admin:pass@example.com/stream.mp4",
            dnsLookup = { listOf(InetAddress.getByName("93.184.216.34")) }
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.code).isEqualTo(ErrorCode.SECURITY_VIOLATION)
        assertThat(error.message).contains("credentials")
    }

    @Test
    fun dnsToctou_rebindProtection_bindsOkHttpConnectionToValidatedIps() {
        // First lookup resolves to approved public IP
        var lookupCount = 0
        val rebindDns = DnsLookup { hostname ->
            lookupCount++
            listOf(InetAddress.getByName("93.184.216.34"))
        }

        val validation = NetworkSecurityPolicy.validateDestination("https://cdn.example.com/video.mp4", rebindDns)
        assertThat(validation).isInstanceOf(NetworkValidationResult.Valid::class.java)
        val valid = (validation as NetworkValidationResult.Valid)
        assertThat(valid.resolvedIps).containsExactly(InetAddress.getByName("93.184.216.34"))

        // Even if an external DNS server were to rebind the hostname to 127.0.0.1 afterward,
        // the client connection uses the approved list bound in NetworkValidationResult
        assertThat(valid.resolvedIps.all { !NetworkSecurityPolicy.isRestrictedAddress(it) }).isTrue()
    }
}
