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
    fun tier2_systemCompanion_capabilityIsTrue() = runTest {
        val engine = DefaultCaptureEngine(
            storageDir = tempFolder.root,
            captureTier = CallCaptureTier.TIER_2_SYSTEM_COMPANION
        )

        val capability = engine.checkCapability()
        assertThat(capability.isSupported).isTrue()
        assertThat(capability.captureEngineType).isEqualTo("ROOT_COMPANION_ALSA")
    }

    @Test
    fun tier2_systemCompanion_startCapture_succeedsAndEntersRecordingState() = runTest {
        val engine = DefaultCaptureEngine(
            storageDir = tempFolder.root,
            captureTier = CallCaptureTier.TIER_2_SYSTEM_COMPANION
        )

        val result = engine.startCapture("call-202", "+905559876543")

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(engine.activeState.value).isNotNull()
        assertThat(engine.activeState.value?.callId).isEqualTo("call-202")
        assertThat(engine.activeState.value?.state).isEqualTo(CallLifecycleState.RECORDING)
    }

    @Test
    fun cancelCapture_resetsActiveStateToNull() = runTest {
        val engine = DefaultCaptureEngine(
            storageDir = tempFolder.root,
            captureTier = CallCaptureTier.TIER_2_SYSTEM_COMPANION
        )

        engine.startCapture("call-303", "+905551112233")
        assertThat(engine.activeState.value).isNotNull()

        val cancelResult = engine.cancelCapture("call-303")
        assertThat(cancelResult).isInstanceOf(AppResult.Success::class.java)
        assertThat(engine.activeState.value).isNull()
    }

    @Test
    fun stopCapture_whenNoFileRecorded_returnsError() = runTest {
        val engine = DefaultCaptureEngine(
            storageDir = tempFolder.root,
            captureTier = CallCaptureTier.TIER_2_SYSTEM_COMPANION
        )

        engine.startCapture("call-404", "+905550000000")
        val stopResult = engine.stopCapture("call-404")

        assertThat(stopResult).isInstanceOf(AppResult.Error::class.java)
        val errorMessage = (stopResult as AppResult.Error).message
        assertThat(errorMessage).contains("No physical audio stream was recorded on disk")
        assertThat(engine.activeState.value).isNull()
    }
}
