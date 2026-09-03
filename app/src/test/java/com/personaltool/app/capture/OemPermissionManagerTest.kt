package com.personaltool.app.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OemPermissionManagerTest {

    @Test
    fun oemPermissionState_enum_hasAccurateDisplayNames() {
        assertThat(OemPermissionState.GRANTED.displayName).contains("Granted")
        assertThat(OemPermissionState.DENIED.displayName).contains("Permission Required")
        assertThat(OemPermissionState.PERMANENTLY_DENIED.displayName).contains("Settings")
    }

    @Test
    fun getRequiredPermission_returnsValidPermissionString() {
        val permission = OemPermissionManager.getRequiredPermission()
        assertThat(permission).isNotNull()
        assertThat(permission.startsWith("android.permission.")).isTrue()
    }

    @Test
    fun evaluatePermissionState_whenGranted_returnsGranted() {
        val state = OemPermissionManager.evaluatePermissionState(
            hasPermission = true,
            hasRequestedBefore = false,
            shouldShowRationale = false
        )
        assertThat(state).isEqualTo(OemPermissionState.GRANTED)
    }

    @Test
    fun evaluatePermissionState_whenDeniedFirstTime_returnsDenied() {
        val state = OemPermissionManager.evaluatePermissionState(
            hasPermission = false,
            hasRequestedBefore = false,
            shouldShowRationale = true
        )
        assertThat(state).isEqualTo(OemPermissionState.DENIED)
    }

    @Test
    fun evaluatePermissionState_whenDeniedWithRationale_returnsDenied() {
        val state = OemPermissionManager.evaluatePermissionState(
            hasPermission = false,
            hasRequestedBefore = true,
            shouldShowRationale = true
        )
        assertThat(state).isEqualTo(OemPermissionState.DENIED)
    }

    @Test
    fun evaluatePermissionState_whenDeniedWithoutRationaleAfterPreviousRequest_returnsPermanentlyDenied() {
        val state = OemPermissionManager.evaluatePermissionState(
            hasPermission = false,
            hasRequestedBefore = true,
            shouldShowRationale = false
        )
        assertThat(state).isEqualTo(OemPermissionState.PERMANENTLY_DENIED)
    }

    @Test
    fun evaluatePermissionState_whenKnownPermanentlyDenied_retainsPermanentDenialEvenWithoutRationale() {
        // P1-PREFLIGHT-26: When rationale information is temporarily unavailable (shouldShowRationale = null),
        // a known permanent denial MUST NOT be downgraded to ordinary DENIED.
        val state = OemPermissionManager.evaluatePermissionState(
            hasPermission = false,
            hasRequestedBefore = true,
            shouldShowRationale = null,
            isKnownPermanentlyDenied = true
        )
        assertThat(state).isEqualTo(OemPermissionState.PERMANENTLY_DENIED)
    }

    @Test
    fun evaluatePermissionState_whenPermissionIsGranted_clearsPermanentDenial() {
        // P1-PREFLIGHT-26: Returning from Settings with permission granted immediately upgrades to GRANTED
        val state = OemPermissionManager.evaluatePermissionState(
            hasPermission = true,
            hasRequestedBefore = true,
            shouldShowRationale = null,
            isKnownPermanentlyDenied = true
        )
        assertThat(state).isEqualTo(OemPermissionState.GRANTED)
    }
}
