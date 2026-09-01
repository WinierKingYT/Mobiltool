package com.personaltool.core.jobs

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkManager

class JobSchedulerHelper(private val context: Context) {
    private val workManager by lazy { WorkManager.getInstance(context) }

    fun createStandardDownloadConstraints(requireWifi: Boolean = false, requireCharging: Boolean = false): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(if (requireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(requireCharging)
            .setRequiresBatteryNotLow(true)
            .build()
    }

    fun createHeavyJobConstraints(requireCharging: Boolean = true): Constraints {
        return Constraints.Builder()
            .setRequiresCharging(requireCharging)
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(false)
            .build()
    }

    fun cancelAllJobsByTag(tag: String) {
        workManager.cancelAllWorkByTag(tag)
    }
}
