package com.personaltool.app.capture

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

sealed class CompanionCaptureResult {
    data class Success(val recordedFile: File, val bytesCaptured: Long) : CompanionCaptureResult()
    data class Failure(val reason: String) : CompanionCaptureResult()
}

object PrivilegedCompanionClient {

    private const val PRIMARY_SOCKET_PATH = "/dev/socket/mobiltool_companion"
    private const val FALLBACK_SOCKET_PATH = "/data/local/tmp/mobiltool_companion.sock"
    private const val MAGIC_HEADER = "MOBILTOOL_COMPANION_V1\n"
    private const val CMD_PING = "PING\n"
    private const val CMD_START = "START_CAPTURE\n"
    private const val CMD_STOP = "STOP_CAPTURE\n"
    private const val RESP_PONG = "PONG"
    private const val RESP_OK = "OK"

    private val isCapturing = AtomicBoolean(false)
    private var activeSocket: LocalSocket? = null
    private var captureThread: Thread? = null

    /**
     * Checks if a live privileged companion daemon is reachable via UNIX domain socket.
     * Fails closed if socket does not exist or handshake fails.
     */
    fun isCompanionActive(): Boolean {
        return try {
            connectAndPing(PRIMARY_SOCKET_PATH) || connectAndPing(FALLBACK_SOCKET_PATH)
        } catch (_: Exception) {
            false
        }
    }

    private fun connectAndPing(path: String): Boolean {
        val socket = LocalSocket()
        return try {
            val address = LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM)
            socket.connect(address)
            socket.soTimeout = 1000

            val output = socket.outputStream ?: return false
            val input = socket.inputStream ?: return false

            output.write(MAGIC_HEADER.toByteArray(Charsets.UTF_8))
            output.write(CMD_PING.toByteArray(Charsets.UTF_8))
            output.flush()

            val buffer = ByteArray(64)
            val bytesRead = input.read(buffer)
            if (bytesRead > 0) {
                val response = String(buffer, 0, bytesRead, Charsets.UTF_8).trim()
                response == RESP_PONG || response.startsWith(RESP_OK)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * Initiates bidirectional privileged capture over the companion socket into [outputFile].
     */
    fun startCapture(
        callId: String,
        phoneNumber: String,
        outputFile: File,
        onCaptureComplete: (CompanionCaptureResult) -> Unit
    ): Boolean {
        if (!isCapturing.compareAndSet(false, true)) {
            return false
        }

        val socket = LocalSocket()
        val connected = try {
            val address = LocalSocketAddress(PRIMARY_SOCKET_PATH, LocalSocketAddress.Namespace.FILESYSTEM)
            socket.connect(address)
            if (socket.outputStream == null || socket.inputStream == null) {
                throw IllegalStateException("Socket streams unavailable")
            }
            true
        } catch (_: Exception) {
            try {
                val fallbackAddr = LocalSocketAddress(FALLBACK_SOCKET_PATH, LocalSocketAddress.Namespace.FILESYSTEM)
                socket.connect(fallbackAddr)
                if (socket.outputStream == null || socket.inputStream == null) {
                    throw IllegalStateException("Fallback socket streams unavailable")
                }
                true
            } catch (err: Exception) {
                isCapturing.set(false)
                onCaptureComplete(CompanionCaptureResult.Failure("Failed to connect to companion daemon socket: ${err.message}"))
                return false
            }
        }

        if (!connected) {
            isCapturing.set(false)
            return false
        }

        activeSocket = socket

        captureThread = Thread({
            var totalBytes = 0L
            try {
                val output = socket.outputStream ?: throw IllegalStateException("outputStream is null")
                val input = socket.inputStream ?: throw IllegalStateException("inputStream is null")

                // Handshake & Start Command
                output.write(MAGIC_HEADER.toByteArray(Charsets.UTF_8))
                output.write("$CMD_START:$callId:$phoneNumber\n".toByteArray(Charsets.UTF_8))
                output.flush()

                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(4096)
                    while (isCapturing.get()) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        if (read > 0) {
                            fos.write(buffer, 0, read)
                            totalBytes += read
                        }
                    }
                    fos.flush()
                }

                if (totalBytes > 0) {
                    onCaptureComplete(CompanionCaptureResult.Success(outputFile, totalBytes))
                } else {
                    onCaptureComplete(CompanionCaptureResult.Failure("Zero bytes received from companion daemon."))
                }
            } catch (err: Exception) {
                onCaptureComplete(CompanionCaptureResult.Failure("Companion stream interrupted: ${err.message}"))
            } finally {
                runCatching { socket.close() }
                activeSocket = null
                isCapturing.set(false)
            }
        }, "PrivilegedCompanionCaptureThread").apply { start() }

        return true
    }

    /**
     * Signals the companion daemon to conclude the audio capture session.
     */
    fun stopCapture() {
        if (isCapturing.compareAndSet(true, false)) {
            try {
                activeSocket?.let { socket ->
                    val output = socket.outputStream
                    output?.write(CMD_STOP.toByteArray(Charsets.UTF_8))
                    output?.flush()
                }
            } catch (_: Exception) {
                // Ignore socket teardown errors
            } finally {
                runCatching { activeSocket?.close() }
                activeSocket = null
            }
        }
    }
}
