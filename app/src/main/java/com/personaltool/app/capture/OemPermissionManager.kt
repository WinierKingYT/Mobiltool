package com.personaltool.app.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class OemPermissionState(val displayName: String) {
    GRANTED("Access Granted"),
    DENIED("Permission Required"),
    PERMANENTLY_DENIED("Settings Configuration Required")
}

object OemPermissionManager {

    fun getRequiredPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun hasPermission(context: Context): Boolean {
        val permission = getRequiredPermission()
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getPermissionState(context: Context): OemPermissionState {
        return if (hasPermission(context)) {
            OemPermissionState.GRANTED
        } else {
            OemPermissionState.DENIED
        }
    }
}
