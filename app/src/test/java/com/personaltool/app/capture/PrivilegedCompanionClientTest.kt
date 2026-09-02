package com.personaltool.app.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class PrivilegedCompanionClientTest {

    @Test
    fun isCompanionActive_whenNoDaemonRunning_returnsFalseFailsClosed() {
        val isActive = PrivilegedCompanionClient.isCompanionActive()
        assertThat(isActive).isFalse()
    }

    @Test
    fun startCapture_whenSocketUnavailable_reportsFailureWithoutCrashing() {
        var failureReported = false
        val tempFile = File.createTempFile("test_capture", ".m4a")
        tempFile.deleteOnExit()

        val started = PrivilegedCompanionClient.startCapture(
            callId = "call-1",
            phoneNumber = "+905550000000",
            outputFile = tempFile
        ) { result ->
            if (result is CompanionCaptureResult.Failure) {
                failureReported = true
            }
        }

        // Since socket does not exist in test runner, either started is false or failure reported
        assertThat(!started || failureReported).isTrue()
        PrivilegedCompanionClient.stopCapture()
    }
}
