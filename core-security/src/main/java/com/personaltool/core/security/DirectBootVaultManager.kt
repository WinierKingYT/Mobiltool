package com.personaltool.core.security

import java.io.File

class DirectBootVaultManager(
    private val deviceProtectedStorageDir: File,
    private val credentialEncryptedVaultDir: File
) {

    fun stageDirectBootRecording(callId: String, data: ByteArray): File {
        deviceProtectedStorageDir.mkdirs()
        val tempFile = File(deviceProtectedStorageDir, "de-staged-call-$callId.tmp")
        tempFile.writeBytes(data)
        return tempFile
    }

    fun migrateStagedToCredentialVault(encryptor: KeystoreVaultEncryptor): Int {
        if (!deviceProtectedStorageDir.exists()) return 0
        val stagedFiles = deviceProtectedStorageDir.listFiles { _, name -> name.startsWith("de-staged-") } ?: return 0
        var migratedCount = 0

        for (file in stagedFiles) {
            val destination = File(credentialEncryptedVaultDir, file.name.removePrefix("de-staged-").replace(".tmp", ".enc"))
            val success = encryptor.encryptFile(file, destination)
            if (success) {
                file.delete()
                migratedCount++
            }
        }
        return migratedCount
    }
}
