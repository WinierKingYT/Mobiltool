package com.personaltool.desktop.bridge.pairing

import com.personaltool.desktop.bridge.model.PairingToken
import java.security.SecureRandom
import java.util.UUID

class PairingManager(
    private val workstationId: String = "WS-WIN11-MAIN"
) {
    private val pairedDevices = mutableSetOf<String>()
    private var currentActiveToken: PairingToken? = null

    fun generatePairingToken(): PairingToken {
        val code = String.format("%06d", SecureRandom().nextInt(1000000))
        val fingerprint = UUID.randomUUID().toString().take(12)
        val token = PairingToken(
            pairingCode = code,
            qrPayload = "personaltool://pair?ws=$workstationId&code=$code&fp=$fingerprint",
            fingerprint = fingerprint,
            workstationId = workstationId,
            expiresAtEpochMs = System.currentTimeMillis() + 300000L // 5 minutes
        )
        currentActiveToken = token
        return token
    }

    fun verifyAndPair(pairingCode: String, deviceId: String): Boolean {
        val active = currentActiveToken ?: return false
        if (System.currentTimeMillis() > active.expiresAtEpochMs) {
            return false
        }
        if (active.pairingCode == pairingCode.trim()) {
            pairedDevices.add(deviceId)
            currentActiveToken = null
            return true
        }
        return false
    }

    fun isDevicePaired(deviceId: String): Boolean = pairedDevices.contains(deviceId)

    fun revokeDevice(deviceId: String): Boolean = pairedDevices.remove(deviceId)
}
