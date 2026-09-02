package com.personaltool.app.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class OemPermissionState(val displayName: String) {
    GRANTED("Access Granted"),
    DENIED("Permission Required"),
    PERMANENTLY_DENIED("Permission Blocked — Open Settings")
}

object OemPermissionManager {

    private const val PREFS_NAME = "oem_permission_prefs"
    private const val KEY_REQUESTED_BEFORE = "has_requested_media_permission"

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

    /**
     * Pure, directly testable permission state evaluator.
     */
    fun evaluatePermissionState(
        hasPermission: Boolean,
        hasRequestedBefore: Boolean,
        shouldShowRationale: Boolean
    ): OemPermissionState {
        return when {
            hasPermission -> OemPermissionState.GRANTED
            hasRequestedBefore && !shouldShowRationale -> OemPermissionState.PERMANENTLY_DENIED
            else -> OemPermissionState.DENIED
        }
    }

    fun getPermissionState(context: Context, shouldShowRationale: Boolean = true): OemPermissionState {
        val granted = hasPermission(context)
        if (granted) return OemPermissionState.GRANTED

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasRequestedBefore = prefs.getBoolean(KEY_REQUESTED_BEFORE, false)

        return evaluatePermissionState(
            hasPermission = false,
            hasRequestedBefore = hasRequestedBefore,
            shouldShowRationale = shouldShowRationale
        )
    }

    fun markPermissionRequested(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_REQUESTED_BEFORE, true).apply()
    }

    fun createAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
