package com.personaltool.call.capture.api

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.CallLifecycleState
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultCaptureEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun tier1_standardUserspace_capabilityIsFalse() = runTest {
        val engine = DefaultCaptureEngine(
            storageDir = tempFolder.root,
            captureTier = CallCaptureTier.TIER_1_STANDARD_USERSPACE
        )

        val capability = engine.checkCapability()
        assertThat(capability.isSupported).isFalse()
        assertThat(capability.requiresSystemPrivilege).isTrue()
        assertThat(capability.captureEngineType).isEqualTo("RESTRICTED_AOSP_USERSPACE")
    }

    @Test
    fun tier1_standardUserspace_startCapture_returnsErrorAndStateRemainsNull() = runTest {
        val engine = DefaultCaptureEngine(
            storageDir = tempFolder.root,
            captureTier = CallCaptureTier.TIER_1_STANDARD_USERSPACE
        )

        val result = engine.startCapture("call-101", "+905551234567")

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val errorMessage = (result as AppResult.Error).message
        assertThat(errorMessage).contains("blocked on this device")
        assertThat(engine.activeState.value).isNull()
    }

    @Test
    fun tier2_unlinkedCompanion_inP0_failsClosed() = runTest {
        val engine = DefaultCaptureEngine(
            storageDir = tempFolder.root,
            captureTier = CallCaptureTier.TIER_2_SYSTEM_COMPANION
        )

        val capability = engine.checkCapability()
        // Invariant: Unlinked companion in P0 must fail closed
        assertThat(capability.isSupported).isFalse()
        assertThat(capability.captureEngineType).isEqualTo("UNLINKED_COMPANION")
    }

    @Test
    fun unlinkedEngine_startCapture_returnsBlockedError() = runTest {
        val engine = DefaultCaptureEngine(
            storageDir = tempFolder.root,
            captureTier = CallCaptureTier.TIER_2_SYSTEM_COMPANION
        )

        val result = engine.startCapture("call-202", "+905559876543")

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        assertThat((result as AppResult.Error).message).contains("blocked on this device")
        assertThat(engine.activeState.value).isNull()
    }

    @Test
    fun micAndUserspace_neverProducesVerifiedBidirectional() = runTest {
        val engine = DefaultCaptureEngine(
            storageDir = tempFolder.root,
            captureTier = CallCaptureTier.TIER_1_STANDARD_USERSPACE,
            isLoudspeakerActive = false
        )

        val capability = engine.checkCapability()
        assertThat(capability.isSupported).isFalse()
    }
}
