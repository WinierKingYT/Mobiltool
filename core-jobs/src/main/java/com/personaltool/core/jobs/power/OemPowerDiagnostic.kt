package com.personaltool.core.jobs.power

enum class OemVendor(val brand: String, val advisory: String) {
    XIAOMI("Xiaomi / Redmi / POCO", "MIUI/HyperOS battery saver restricts background downloads. Set app to 'No restrictions'."),
    SAMSUNG("Samsung", "OneUI puts background services to sleep. Exclude PersonalTool from 'Sleeping apps'."),
    HUAWEI("Huawei / Honor", "EMUI PowerGenie kills foreground tasks. Enable 'Manual launch & run in background'."),
    GENERIC_AOSP("Generic / Pixel / Motorola", "Standard AOSP background policy active. No extra OEM bypass needed.")
}

data class OemDiagnosticReport(
    val vendor: OemVendor,
    val hasAggressiveBatteryKiller: Boolean,
    val recommendedAction: String
)

object OemPowerDiagnostic {

    fun detectOemProfile(manufacturer: String = "Google"): OemDiagnosticReport {
        val lower = manufacturer.lowercase()
        val vendor = when {
            lower.contains("xiaomi") || lower.contains("redmi") || lower.contains("poco") -> OemVendor.XIAOMI
            lower.contains("samsung") -> OemVendor.SAMSUNG
            lower.contains("huawei") || lower.contains("honor") -> OemVendor.HUAWEI
            else -> OemVendor.GENERIC_AOSP
        }

        return OemDiagnosticReport(
            vendor = vendor,
            hasAggressiveBatteryKiller = vendor != OemVendor.GENERIC_AOSP,
            recommendedAction = vendor.advisory
        )
    }
}
