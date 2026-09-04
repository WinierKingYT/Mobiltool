package com.personaltool.app.audio

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

class FakeAudioPlaybackEngine : AudioPlaybackEngine {
    var isPrepared = false
    var isPlaying = false
    var fakeCurrentPosition: Long = 0L
    var fakeDuration: Long = 60000L
    var speed: Float = 1.0f
    var releaseCount = AtomicInteger(0)

    var onPreparedCallback: ((Long) -> Unit)? = null
    var onErrorCallback: ((String) -> Unit)? = null
    var onCompletionCallback: (() -> Unit)? = null
    var onInterruptionCallback: ((AudioInterruptionReason) -> Unit)? = null

    var shouldThrowOnPrepare = false
    var shouldFailStart = false
    var shouldFailSeek = false

    override fun prepare(
        filePath: String,
        onPrepared: (durationMs: Long) -> Unit,
        onError: (errorMessage: String) -> Unit,
        onCompletion: () -> Unit,
        onInterruption: (AudioInterruptionReason) -> Unit
    ) {
        if (shouldThrowOnPrepare) {
            throw IllegalStateException("Forced prepare failure")
        }
        onPreparedCallback = onPrepared
        onErrorCallback = onError
        onCompletionCallback = onCompletion
        onInterruptionCallback = onInterruption
        isPrepared = true
    }

    fun triggerPrepared(durationMs: Long = fakeDuration) {
        fakeDuration = durationMs
        onPreparedCallback?.invoke(durationMs)
    }

    fun triggerError(errorMessage: String) {
        onErrorCallback?.invoke(errorMessage)
    }

    fun triggerCompletion() {
        isPlaying = false
        fakeCurrentPosition = fakeDuration
        onCompletionCallback?.invoke()
    }

    fun triggerInterruption(reason: AudioInterruptionReason = AudioInterruptionReason.SYSTEM_FOCUS_LOSS) {
        isPlaying = false
        onInterruptionCallback?.invoke(reason)
    }

    override fun start(): Boolean {
        if (shouldFailStart) return false
        isPlaying = true
        return true
    }

    override fun pause(): Boolean {
        isPlaying = false
        return true
    }

    override fun seekTo(positionMs: Long): Boolean {
        if (shouldFailSeek) return false
        fakeCurrentPosition = positionMs.coerceIn(0L, fakeDuration)
        return true
    }

    override fun setPlaybackSpeed(speed: Float): Boolean {
        this.speed = speed.coerceIn(0.5f, 2.0f)
        return true
    }

    override fun getCurrentPosition(): Long = fakeCurrentPosition

    override fun getDuration(): Long = fakeDuration

    override fun release() {
        isPlaying = false
        isPrepared = false
        releaseCount.incrementAndGet()
    }
}

class AudioPlaybackControllerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fakeEngine = FakeAudioPlaybackEngine()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun createController(engine: AudioPlaybackEngine = fakeEngine): AudioPlaybackController {
        return AudioPlaybackController(
            engineFactory = { engine },
            coroutineScope = scope
        )
    }

    private fun createTempAudioFile(name: String = "test.m4a", size: Int = 4096): File {
        val file = tempFolder.newFile(name)
        FileOutputStream(file).use { fos ->
            fos.write(ByteArray(size) { 0x11 })
        }
        return file
    }

    // ==========================================
    // STATE TRANSITION TESTS
    // ==========================================

    @Test
    fun initialState_isIdle() {
        val controller = createController()
        val state = controller.state.value

        assertThat(state.phase).isEqualTo(AudioPlaybackPhase.IDLE)
        assertThat(state.currentPositionMs).isEqualTo(0L)
        assertThat(state.durationMs).isEqualTo(0L)
        assertThat(state.canPlay).isFalse()
        assertThat(state.canPause).isFalse()
    }

    @Test
    fun openAudio_withValidFile_transitionsToLoading_thenReadyOnPrepared() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Test Track", file.absolutePath)
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.LOADING)
        assertThat(controller.state.value.targetId).isEqualTo("target-1")
        assertThat(controller.state.value.title).isEqualTo("Test Track")

        fakeEngine.triggerPrepared(30000L)

        val state = controller.state.value
        assertThat(state.phase).isEqualTo(AudioPlaybackPhase.READY)
        assertThat(state.durationMs).isEqualTo(30000L)
        assertThat(state.currentPositionMs).isEqualTo(0L)
        assertThat(state.canPlay).isTrue()
        assertThat(state.canPause).isFalse()
    }

    @Test
    fun play_fromReady_transitionsToPlaying() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Test Track", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)

        controller.play()

        val state = controller.state.value
        assertThat(state.phase).isEqualTo(AudioPlaybackPhase.PLAYING)
        assertThat(state.canPlay).isFalse()
        assertThat(state.canPause).isTrue()
        assertThat(fakeEngine.isPlaying).isTrue()
    }

    @Test
    fun pause_fromPlaying_transitionsToPaused() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Test Track", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)
        controller.play()

        controller.pause()

        val state = controller.state.value
        assertThat(state.phase).isEqualTo(AudioPlaybackPhase.PAUSED)
        assertThat(state.canPlay).isTrue()
        assertThat(state.canPause).isFalse()
        assertThat(fakeEngine.isPlaying).isFalse()
    }

    @Test
    fun togglePlayPause_cyclesBetweenPlayingAndPaused() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Test Track", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)

        controller.togglePlayPause()
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.PLAYING)

        controller.togglePlayPause()
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.PAUSED)

        controller.togglePlayPause()
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.PLAYING)
    }

    @Test
    fun completion_transitionsToCompleted_withPositionAtDuration() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Test Track", file.absolutePath)
        fakeEngine.triggerPrepared(45000L)
        controller.play()

        fakeEngine.triggerCompletion()

        val state = controller.state.value
        assertThat(state.phase).isEqualTo(AudioPlaybackPhase.COMPLETED)
        assertThat(state.currentPositionMs).isEqualTo(45000L)
        assertThat(state.canPlay).isTrue()
        assertThat(state.canPause).isFalse()
    }

    @Test
    fun completedReplay_whenRewindFails_doesNotStartAndRetainsCompletedPosition() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Test Track", file.absolutePath)
        fakeEngine.triggerPrepared(45000L)
        controller.play()
        fakeEngine.triggerCompletion()

        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.COMPLETED)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(45000L)

        // Force seek failure on rewind
        fakeEngine.shouldFailSeek = true

        val started = controller.play()

        assertThat(started).isFalse()
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.COMPLETED)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(45000L)
        assertThat(fakeEngine.isPlaying).isFalse()
    }

    @Test
    fun completedReplay_whenRewindSucceeds_rewindsToZeroAndStartsPlaying() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Test Track", file.absolutePath)
        fakeEngine.triggerPrepared(45000L)
        controller.play()
        fakeEngine.triggerCompletion()

        val started = controller.play()

        assertThat(started).isTrue()
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.PLAYING)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(0L)
        assertThat(fakeEngine.isPlaying).isTrue()
    }

    @Test
    fun error_transitionsToError_withErrorMessage_andReleasesEngine() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Test Track", file.absolutePath)
        fakeEngine.triggerError("Decoder failure")

        val state = controller.state.value
        assertThat(state.phase).isEqualTo(AudioPlaybackPhase.ERROR)
        assertThat(state.errorMessage).isEqualTo("Decoder failure")
        assertThat(state.canPlay).isFalse()
        assertThat(fakeEngine.releaseCount.get()).isEqualTo(1)
    }

    // ==========================================
    // P3-E04-FINAL-01: SESSION GENERATION & STALE CALLBACK TESTS
    // ==========================================

    @Test
    fun sameTarget_openedTwice_latePreparedFromFirstSession_doesNotAffectSecondSession() {
        val file = createTempAudioFile("same.m4a")
        val engine1 = FakeAudioPlaybackEngine()
        val engine2 = FakeAudioPlaybackEngine()

        var callCount = 0
        val controller = AudioPlaybackController(
            engineFactory = {
                callCount++
                if (callCount == 1) engine1 else engine2
            },
            coroutineScope = scope
        )

        // Open session 1
        controller.openAudio("target-A", "Track A", file.absolutePath)
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.LOADING)

        // Reopen same target -> session 2
        controller.openAudio("target-A", "Track A", file.absolutePath)
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.LOADING)

        // Session 1's onPrepared arrives late
        engine1.triggerPrepared(30000L)

        // Controller must still be in LOADING for session 2, not updated by stale session 1
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.LOADING)
        assertThat(controller.state.value.durationMs).isEqualTo(0L)

        // Now session 2's onPrepared arrives
        engine2.triggerPrepared(60000L)
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.READY)
        assertThat(controller.state.value.durationMs).isEqualTo(60000L)
    }

    @Test
    fun differentTarget_opened_latePreparedFromFirstSession_doesNotAffectSecondSession() {
        val fileA = createTempAudioFile("a.m4a")
        val fileB = createTempAudioFile("b.m4a")
        val engine1 = FakeAudioPlaybackEngine()
        val engine2 = FakeAudioPlaybackEngine()

        var callCount = 0
        val controller = AudioPlaybackController(
            engineFactory = {
                callCount++
                if (callCount == 1) engine1 else engine2
            },
            coroutineScope = scope
        )

        controller.openAudio("target-A", "Track A", fileA.absolutePath)
        controller.openAudio("target-B", "Track B", fileB.absolutePath)

        // Late prepared from A
        engine1.triggerPrepared(30000L)
        assertThat(controller.state.value.targetId).isEqualTo("target-B")
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.LOADING)
    }

    @Test
    fun staleErrorCallback_doesNotSetNewSessionToError_andDoesNotReleaseNewEngine() {
        val fileA = createTempAudioFile("a.m4a")
        val fileB = createTempAudioFile("b.m4a")
        val engine1 = FakeAudioPlaybackEngine()
        val engine2 = FakeAudioPlaybackEngine()

        var callCount = 0
        val controller = AudioPlaybackController(
            engineFactory = {
                callCount++
                if (callCount == 1) engine1 else engine2
            },
            coroutineScope = scope
        )

        controller.openAudio("target-A", "Track A", fileA.absolutePath)
        controller.openAudio("target-B", "Track B", fileB.absolutePath)
        engine2.triggerPrepared(60000L)

        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.READY)

        // Old engine 1 triggers error late
        engine1.triggerError("Old error")

        // New session must remain READY and engine2 must NOT be released
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.READY)
        assertThat(controller.state.value.errorMessage).isNull()
        assertThat(engine2.releaseCount.get()).isEqualTo(0)
    }

    @Test
    fun staleCompletionCallback_doesNotSetNewSessionToCompleted() {
        val fileA = createTempAudioFile("a.m4a")
        val fileB = createTempAudioFile("b.m4a")
        val engine1 = FakeAudioPlaybackEngine()
        val engine2 = FakeAudioPlaybackEngine()

        var callCount = 0
        val controller = AudioPlaybackController(
            engineFactory = {
                callCount++
                if (callCount == 1) engine1 else engine2
            },
            coroutineScope = scope
        )

        controller.openAudio("target-A", "Track A", fileA.absolutePath)
        controller.openAudio("target-B", "Track B", fileB.absolutePath)
        engine2.triggerPrepared(60000L)

        // Old engine 1 triggers completion late
        engine1.triggerCompletion()

        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.READY)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(0L)
    }

    @Test
    fun release_invalidatesAllPendingCallbacks() {
        val file = createTempAudioFile("rel.m4a")
        val controller = createController()

        controller.openAudio("target-1", "Track 1", file.absolutePath)
        controller.release()

        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.IDLE)

        // Callbacks arrive after release
        fakeEngine.triggerPrepared(50000L)
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.IDLE)

        fakeEngine.triggerError("Post-release error")
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.IDLE)

        fakeEngine.triggerCompletion()
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.IDLE)
    }

    // ==========================================
    // P3-E04-FINAL-02: TERMINAL ERROR CLEANUP TESTS
    // ==========================================

    @Test
    fun playing_terminalError_setsError_stopsPolling_releasesActiveEngine() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Track 1", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)
        controller.play()

        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.PLAYING)

        fakeEngine.triggerError("Hardware error")

        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.ERROR)
        assertThat(controller.state.value.errorMessage).isEqualTo("Hardware error")
        assertThat(fakeEngine.releaseCount.get()).isEqualTo(1)

        // Advance simulated time - polling must not update state
        fakeEngine.fakeCurrentPosition = 9999L
        runBlocking { delay(300) }
        assertThat(controller.state.value.currentPositionMs).isEqualTo(0L)
    }

    // ==========================================
    // P3-E04-FINAL-03: AUDIO FOCUS PROPAGATION TESTS
    // ==========================================

    @Test
    fun playing_systemFocusLoss_pausesController_andStopsPolling() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Track 1", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)
        controller.play()

        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.PLAYING)

        // System focus loss event
        fakeEngine.triggerInterruption(AudioInterruptionReason.SYSTEM_FOCUS_LOSS)

        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.PAUSED)
        assertThat(controller.state.value.canPlay).isTrue()

        // Advance simulated time - polling must not update state
        fakeEngine.fakeCurrentPosition = 8888L
        runBlocking { delay(300) }
        assertThat(controller.state.value.currentPositionMs).isEqualTo(0L)
    }

    @Test
    fun playing_systemTransientFocusLoss_pausesController_andStopsPolling() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Track 1", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)
        controller.play()

        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.PLAYING)

        // Transient focus loss event
        fakeEngine.triggerInterruption(AudioInterruptionReason.SYSTEM_TRANSIENT_FOCUS_LOSS)

        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.PAUSED)
        assertThat(controller.state.value.canPlay).isTrue()
    }

    // ==========================================
    // FILE VALIDATION AND PREPARATION FAILURE TESTS
    // ==========================================

    @Test
    fun openAudio_nullOrBlankPath_failsClosedWithErrorMessage() {
        val controller = createController()

        controller.openAudio("target-null", "Null File", null)
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.ERROR)
        assertThat(controller.state.value.errorMessage).contains("missing or blank")

        controller.openAudio("target-blank", "Blank File", "   ")
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.ERROR)
    }

    @Test
    fun openAudio_missingFile_failsClosedWithErrorMessage() {
        val controller = createController()
        val missing = File(tempFolder.root, "does_not_exist.m4a").absolutePath

        controller.openAudio("target-missing", "Missing File", missing)
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.ERROR)
        assertThat(controller.state.value.errorMessage).contains("not found")
    }

    @Test
    fun prepareThrows_releasesEngineAndTransitionsToError() {
        fakeEngine.shouldThrowOnPrepare = true
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-throw", "Throw Track", file.absolutePath)

        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.ERROR)
        assertThat(fakeEngine.releaseCount.get()).isGreaterThan(0)
    }

    // ==========================================
    // SEEK AND SPEED CLAMPING TESTS
    // ==========================================

    @Test
    fun seekTo_clampsNegativeToZero_andOverDurationToDuration() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Track", file.absolutePath)
        fakeEngine.triggerPrepared(50000L)

        // Negative clamp
        controller.seekTo(-5000L)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(0L)

        // Over duration clamp
        controller.seekTo(90000L)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(50000L)

        // Valid within bounds
        controller.seekTo(25000L)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(25000L)
    }

    @Test
    fun setSpeed_clampsBetween0_5And2_0() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Track", file.absolutePath)
        fakeEngine.triggerPrepared(50000L)

        // Below 0.5
        controller.setSpeed(0.1f)
        assertThat(controller.state.value.playbackSpeed).isEqualTo(0.5f)

        // Exact 0.5
        controller.setSpeed(0.5f)
        assertThat(controller.state.value.playbackSpeed).isEqualTo(0.5f)

        // Standard 1.0
        controller.setSpeed(1.0f)
        assertThat(controller.state.value.playbackSpeed).isEqualTo(1.0f)

        // Fast 1.5
        controller.setSpeed(1.5f)
        assertThat(controller.state.value.playbackSpeed).isEqualTo(1.5f)

        // Exact 2.0
        controller.setSpeed(2.0f)
        assertThat(controller.state.value.playbackSpeed).isEqualTo(2.0f)

        // Above 2.0
        controller.setSpeed(3.5f)
        assertThat(controller.state.value.playbackSpeed).isEqualTo(2.0f)
    }

    // ==========================================
    // SWITCHING AND IDEMPOTENT RELEASE TESTS
    // ==========================================

    @Test
    fun switchingAudio_releasesPreviousSessionExactlyOnce() {
        val fileA = createTempAudioFile("a.m4a")
        val fileB = createTempAudioFile("b.m4a")

        val engineA = FakeAudioPlaybackEngine()
        val engineB = FakeAudioPlaybackEngine()

        var callCount = 0
        val controller = AudioPlaybackController(
            engineFactory = {
                callCount++
                if (callCount == 1) engineA else engineB
            },
            coroutineScope = scope
        )

        controller.openAudio("target-A", "Track A", fileA.absolutePath)
        engineA.triggerPrepared(30000L)
        assertThat(controller.state.value.targetId).isEqualTo("target-A")

        controller.openAudio("target-B", "Track B", fileB.absolutePath)
        engineB.triggerPrepared(40000L)

        assertThat(engineA.releaseCount.get()).isEqualTo(1)
        assertThat(controller.state.value.targetId).isEqualTo("target-B")
        assertThat(controller.state.value.durationMs).isEqualTo(40000L)
    }

    @Test
    fun release_isIdempotentAndSafeToCallMultipleTimes() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Track", file.absolutePath)
        fakeEngine.triggerPrepared(50000L)

        controller.release()
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.IDLE)

        // Second release must not crash
        controller.release()
        assertThat(controller.state.value.phase).isEqualTo(AudioPlaybackPhase.IDLE)
    }

    @Test
    fun realPosition_comesFromEngine_neverIncrementedSynthetically() {
        val file = createTempAudioFile()
        val controller = createController()

        controller.openAudio("target-1", "Track", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)
        controller.play()

        // Simulate engine position advancing to 12345
        fakeEngine.fakeCurrentPosition = 12345L

        runBlocking {
            delay(300) // Allow polling loop to tick
        }

        assertThat(controller.state.value.currentPositionMs).isEqualTo(12345L)
    }
}
