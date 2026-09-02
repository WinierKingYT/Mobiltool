package com.personaltool.app.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class OemRecordingImporterTest {

    @Test
    fun findAndImport_whenNoDirectoriesPresent_returnsNotFoundSafely() {
        val targetDir = File.createTempFile("vault_test", "dir")
        targetDir.delete()
        targetDir.mkdirs()
        targetDir.deleteOnExit()

        val result = OemRecordingImporter.findAndImport(
            phoneNumber = "+905551234567",
            startTimeMs = 1700000000000L,
            endTimeMs = 1700000030000L,
            targetVaultDir = targetDir
        )

        // On JVM host unit tests without OEM recording folders, returns NotFound diagnostic
        assertThat(result).isInstanceOf(OemImportResult.NotFound::class.java)
    }
}
