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
}
