package com.personaltool.media.extractor.api.youtube

import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.atomic.AtomicReference

/**
 * Mobiltool-owned process-global runtime initialization boundary for NewPipe (P2-YT-FINAL-03).
 *
 * Enforces:
 * 1. Explicit initialization of NewPipe with an approved, hardened NewPipeDownloaderBridge.
 * 2. Idempotent success when re-initialized with the identical or compatible bridge.
 * 3. Fail-fast error (IllegalStateException) if an attempt is made to overwrite with a different/incompatible bridge.
 * 4. Observable initialization state.
 * 5. Test isolation support via resetForTesting().
 */
object NewPipeRuntime {

    private val initializedBridge = AtomicReference<NewPipeDownloaderBridge?>(null)

    @Synchronized
    fun ensureInitialized(bridge: NewPipeDownloaderBridge = NewPipeDownloaderBridge()) {
        val current = initializedBridge.get()
        if (current == null) {
            NewPipe.init(bridge)
            initializedBridge.set(bridge)
        } else if (current != bridge && current !== bridge) {
            throw IllegalStateException(
                "NewPipeRuntime already initialized with a different DownloaderBridge. " +
                "Silent replacement or alteration of the secured bridge is prohibited."
            )
        }
    }

    fun isInitialized(): Boolean = initializedBridge.get() != null

    fun getActiveBridge(): NewPipeDownloaderBridge? = initializedBridge.get()

    @Synchronized
    fun resetForTesting() {
        initializedBridge.set(null)
    }
}
