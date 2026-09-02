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
        // User clicked "Don't ask again" or OS blocked further dialogs:
        // hasPermission = false, hasRequestedBefore = true, shouldShowRationale = false
        val state = OemPermissionManager.evaluatePermissionState(
            hasPermission = false,
            hasRequestedBefore = true,
            shouldShowRationale = false
        )
        assertThat(state).isEqualTo(OemPermissionState.PERMANENTLY_DENIED)
    }
}
