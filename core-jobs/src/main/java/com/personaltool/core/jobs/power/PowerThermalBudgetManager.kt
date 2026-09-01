package com.personaltool.core.jobs.power

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PowerClass(val code: String, val description: String) {
    CLASS_0_IDLE("CLASS_0", "Zero CPU / Idle Standby"),
    CLASS_1_LIGHT("CLASS_1", "UI Navigation & Metadata Probe"),
    CLASS_2_NETWORK("CLASS_2", "Active Media Downloader"),
    CLASS_3_COMPUTE("CLASS_3", "On-Device STT / Audio Transcode"),
    CLASS_4_REALTIME("CLASS_4", "Active Call Recording / Realtime Stream")
}

enum class ThermalHeadroom {
    NORMAL,
    WARM,
    THROTTLED,
    CRITICAL
}

data class PowerThermalState(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val thermalStatus: ThermalHeadroom = ThermalHeadroom.NORMAL,
    val activePowerClass: PowerClass = PowerClass.CLASS_0_IDLE,
    val canRunHeavyCompute: Boolean = true
)

class PowerThermalBudgetManager(
    private val context: Context
) {

    private val _state = MutableStateFlow(PowerThermalState())
    val state: StateFlow<PowerThermalState> = _state.asStateFlow()

    init {
        refreshState()
    }

    fun refreshState() {
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, batteryFilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = if (scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 100

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = powerManager?.isPowerSaveMode == true

        val thermalHeadroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE,
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalHeadroom.NORMAL
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalHeadroom.WARM
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalHeadroom.THROTTLED
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalHeadroom.CRITICAL
                else -> ThermalHeadroom.NORMAL
            }
        } else {
            ThermalHeadroom.NORMAL
        }

        // Heavy compute policy: refuse if battery < 15% and not charging, or if throttled
        val canCompute = (batteryPct >= 15 || isCharging) && thermalHeadroom != ThermalHeadroom.CRITICAL

        _state.update {
            it.copy(
                batteryPercent = batteryPct,
                isCharging = isCharging,
                isPowerSaveMode = isPowerSave,
                thermalStatus = thermalHeadroom,
                canRunHeavyCompute = canCompute
            )
        }
    }

    fun setActivePowerClass(powerClass: PowerClass) {
        _state.update { it.copy(activePowerClass = powerClass) }
    }
}
