package com.personaltool.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DirectBootVaultManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun encryptedData_equalityAndHashCode_operateCorrectly() {
        val cipher = byteArrayOf(0x01, 0x02, 0x03)
        val iv = byteArrayOf(0x0A, 0x0B)

        val item1 = EncryptedData(cipher.copyOf(), iv.copyOf())
        val item2 = EncryptedData(cipher.copyOf(), iv.copyOf())

        assertThat(item1).isEqualTo(item2)
        assertThat(item1.hashCode()).isEqualTo(item2.hashCode())
    }

    @Test
    fun stageDirectBootRecording_writesTemporaryFileToDeviceProtectedStorage() {
        val deDir = tempFolder.newFolder("de_storage")
        val ceDir = tempFolder.newFolder("ce_storage")
        val manager = DirectBootVaultManager(deDir, ceDir)

        val sampleAudio = ByteArray(1024) { 0x55 }
        val stagedFile = manager.stageDirectBootRecording("call_test_001", sampleAudio)

        assertThat(stagedFile.exists()).isTrue()
        assertThat(stagedFile.name).isEqualTo("de-staged-call-call_test_001.tmp")
        assertThat(stagedFile.length()).isEqualTo(1024L)
    }
}
