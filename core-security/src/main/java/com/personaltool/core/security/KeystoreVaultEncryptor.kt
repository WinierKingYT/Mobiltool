package com.personaltool.core.security

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class KeystoreVaultEncryptor(
    private val keystoreHelper: KeystoreHelper = KeystoreHelper()
) {

    fun encryptFile(sourceFile: File, destinationFile: File): Boolean {
        return runCatching {
            val plaintext = sourceFile.readBytes()
            val encrypted = keystoreHelper.encrypt(plaintext)

            destinationFile.parentFile?.mkdirs()
            FileOutputStream(destinationFile).use { fos ->
                // Write IV length (1 byte) + IV + Ciphertext
                fos.write(encrypted.iv.size)
                fos.write(encrypted.iv)
                fos.write(encrypted.ciphertext)
            }
            true
        }.getOrDefault(false)
    }

    fun decryptFile(encryptedFile: File, destinationFile: File): Boolean {
        return runCatching {
            FileInputStream(encryptedFile).use { fis ->
                val ivSize = fis.read()
                if (ivSize <= 0) return false
                val iv = ByteArray(ivSize)
                fis.read(iv)
                val ciphertext = fis.readBytes()

                val decrypted = keystoreHelper.decrypt(EncryptedData(ciphertext, iv))
                destinationFile.parentFile?.mkdirs()
                destinationFile.writeBytes(decrypted)
                true
            }
        }.getOrDefault(false)
    }

    fun isVaultActive(): Boolean {
        return runCatching {
            keystoreHelper.getOrCreateSecretKey()
            true
        }.getOrDefault(false)
    }
}
