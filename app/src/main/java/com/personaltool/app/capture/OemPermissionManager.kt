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
    PERMANENTLY_DENIED("Permission Blocked - Open Settings")
}

object OemPermissionManager {

    private const val PREFS_NAME = "oem_permission_prefs"
    private const val KEY_REQUESTED_BEFORE = "has_requested_media_permission"
    private const val KEY_PERMANENTLY_DENIED = "is_permanently_denied"

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
        shouldShowRationale: Boolean?,
        isKnownPermanentlyDenied: Boolean = false
    ): OemPermissionState {
        if (hasPermission) return OemPermissionState.GRANTED

        // If previously verified as permanently denied and permission is still missing, retain permanent denial
        if (isKnownPermanentlyDenied) return OemPermissionState.PERMANENTLY_DENIED

        // If rationale information is available from an Activity after request:
        if (hasRequestedBefore && shouldShowRationale == false) {
            return OemPermissionState.PERMANENTLY_DENIED
        }

        return OemPermissionState.DENIED
    }

    fun getPermissionState(context: Context, shouldShowRationale: Boolean? = null): OemPermissionState {
        val granted = hasPermission(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (granted) {
            // Clear permanent denial flag if permission was granted (e.g. in Android Settings)
            if (prefs.getBoolean(KEY_PERMANENTLY_DENIED, false)) {
                prefs.edit().putBoolean(KEY_PERMANENTLY_DENIED, false).apply()
            }
            return OemPermissionState.GRANTED
        }

        val hasRequestedBefore = prefs.getBoolean(KEY_REQUESTED_BEFORE, false)
        val isKnownPermanentlyDenied = prefs.getBoolean(KEY_PERMANENTLY_DENIED, false)

        val determinedState = evaluatePermissionState(
            hasPermission = false,
            hasRequestedBefore = hasRequestedBefore,
            shouldShowRationale = shouldShowRationale,
            isKnownPermanentlyDenied = isKnownPermanentlyDenied
        )

        if (determinedState == OemPermissionState.PERMANENTLY_DENIED && !isKnownPermanentlyDenied) {
            prefs.edit().putBoolean(KEY_PERMANENTLY_DENIED, true).apply()
        }

        return determinedState
    }

    fun recordPermissionResult(context: Context, isGranted: Boolean, shouldShowRationale: Boolean): OemPermissionState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_REQUESTED_BEFORE, true).apply()

        if (isGranted) {
            prefs.edit().putBoolean(KEY_PERMANENTLY_DENIED, false).apply()
            return OemPermissionState.GRANTED
        }

        val isPermanent = !shouldShowRationale
        prefs.edit().putBoolean(KEY_PERMANENTLY_DENIED, isPermanent).apply()
        return if (isPermanent) OemPermissionState.PERMANENTLY_DENIED else OemPermissionState.DENIED
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
