package com.personaltool.core.jobs.power

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OemPowerDiagnosticTest {

    @Test
    fun xiaomiDevices_areIdentifiedWithAggressiveBatteryKilling() {
        val brands = listOf("Xiaomi", "Redmi", "POCO", "xiaomi 13T")
        for (brand in brands) {
            val report = OemPowerDiagnostic.detectOemProfile(brand)
            assertThat(report.vendor).isEqualTo(OemVendor.XIAOMI)
            assertThat(report.hasAggressiveBatteryKiller).isTrue()
            assertThat(report.recommendedAction).contains("No restrictions")
        }
    }

    @Test
    fun samsungDevices_areIdentifiedWithSleepingAppsAdvisory() {
        val report = OemPowerDiagnostic.detectOemProfile("Samsung Galaxy S24")
        assertThat(report.vendor).isEqualTo(OemVendor.SAMSUNG)
        assertThat(report.hasAggressiveBatteryKiller).isTrue()
        assertThat(report.recommendedAction).contains("Sleeping apps")
    }

    @Test
    fun huaweiDevices_areIdentifiedWithPowerGenieAdvisory() {
        val report = OemPowerDiagnostic.detectOemProfile("Huawei P60 Pro")
        assertThat(report.vendor).isEqualTo(OemVendor.HUAWEI)
        assertThat(report.hasAggressiveBatteryKiller).isTrue()
        assertThat(report.recommendedAction).contains("run in background")
    }

    @Test
    fun googlePixelAndAosp_identifiedWithoutAggressiveKiller() {
        val report = OemPowerDiagnostic.detectOemProfile("Google Pixel 9")
        assertThat(report.vendor).isEqualTo(OemVendor.GENERIC_AOSP)
        assertThat(report.hasAggressiveBatteryKiller).isFalse()
    }
}
