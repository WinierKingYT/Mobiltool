package com.personaltool.desktop.bridge.transport

import com.personaltool.desktop.bridge.model.EndToEndProof
import com.personaltool.desktop.bridge.model.NetworkProfile
import com.personaltool.desktop.bridge.model.TransportMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TransportState(
    val currentTransport: TransportMode = TransportMode.DIRECT_LAN,
    val networkProfile: NetworkProfile = NetworkProfile.WIFI_UNMETERED,
    val isReachable: Boolean = true,
    val rttLatencyMs: Int = 12,
    val lastHeartbeatEpochMs: Long = System.currentTimeMillis(),
    val e2eProof: EndToEndProof = EndToEndProof(
        deviceFingerprint = "fp-android-9a4f2",
        workstationFingerprint = "fp-win11-7c8e1",
        sessionKeyId = "k-e2ee-session-883"
    )
)

class RemotePresenceManager {

    private val _transportState = MutableStateFlow(TransportState())
    val transportState: StateFlow<TransportState> = _transportState.asStateFlow()

    fun switchTransportMode(mode: TransportMode) {
        _transportState.update {
            it.copy(
                currentTransport = mode,
                rttLatencyMs = mode.typicalLatencyMs,
                lastHeartbeatEpochMs = System.currentTimeMillis()
            )
        }
    }

    fun setNetworkProfile(profile: NetworkProfile) {
        _transportState.update {
            it.copy(
                networkProfile = profile,
                currentTransport = if (profile == NetworkProfile.CELLULAR_METERED && it.currentTransport == TransportMode.DIRECT_LAN) {
                    TransportMode.P2P_HOLEPUNCH
                } else {
                    it.currentTransport
                }
            )
        }
    }

    fun recordHeartbeat() {
        _transportState.update {
            it.copy(lastHeartbeatEpochMs = System.currentTimeMillis(), isReachable = true)
        }
    }

    fun triggerAutomatedFallback(): TransportMode {
        val current = _transportState.value.currentTransport
        val next = when (current) {
            TransportMode.DIRECT_LAN -> TransportMode.P2P_HOLEPUNCH
            TransportMode.P2P_HOLEPUNCH -> TransportMode.RELAY_ENCRYPTED
            TransportMode.RELAY_ENCRYPTED -> TransportMode.DIRECT_LAN
        }
        switchTransportMode(next)
        return next
    }
}
