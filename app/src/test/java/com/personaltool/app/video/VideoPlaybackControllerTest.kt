package com.personaltool.app.video

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

class FakeVideoPlaybackEngine : VideoPlaybackEngine {
    var isPrepared = false
    var isPlaying = false
    var fakeCurrentPosition: Long = 0L
    var fakeDuration: Long = 120000L
    var fakeWidth: Int = 1920
    var fakeHeight: Int = 1080
    var speed: Float = 1.0f
    var releaseCount = AtomicInteger(0)

    var onPreparedCallback: ((Long, Int, Int) -> Unit)? = null
    var onErrorCallback: ((String) -> Unit)? = null
    var onCompletionCallback: (() -> Unit)? = null

    var shouldThrowOnPrepare = false
    var shouldFailStart = false
    var shouldFailSeek = false

    override fun prepare(
        filePath: String,
        onPrepared: (durationMs: Long, width: Int, height: Int) -> Unit,
        onError: (errorMessage: String) -> Unit,
        onCompletion: () -> Unit
    ) {
        if (shouldThrowOnPrepare) {
            throw IllegalStateException("Forced prepare failure")
        }
        onPreparedCallback = onPrepared
        onErrorCallback = onError
        onCompletionCallback = onCompletion
        isPrepared = true
    }

    fun triggerPrepared(durationMs: Long = fakeDuration, width: Int = fakeWidth, height: Int = fakeHeight) {
        fakeDuration = durationMs
        fakeWidth = width
        fakeHeight = height
        onPreparedCallback?.invoke(durationMs, width, height)
    }

    fun triggerError(errorMessage: String) {
        onErrorCallback?.invoke(errorMessage)
    }

    fun triggerCompletion() {
        isPlaying = false
        fakeCurrentPosition = fakeDuration
        onCompletionCallback?.invoke()
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

class VideoPlaybackControllerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fakeEngine = FakeVideoPlaybackEngine()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun createController(engine: VideoPlaybackEngine = fakeEngine): VideoPlaybackController {
        return VideoPlaybackController(
            engineFactory = { engine },
            coroutineScope = scope
        )
    }

    private fun createTempVideoFile(name: String = "test_video.mp4", size: Int = 8192): File {
        val file = tempFolder.newFile(name)
        FileOutputStream(file).use { fos ->
            fos.write(ByteArray(size) { 0x22 })
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

        assertThat(state.phase).isEqualTo(VideoPlaybackPhase.IDLE)
        assertThat(state.currentPositionMs).isEqualTo(0L)
        assertThat(state.durationMs).isEqualTo(0L)
        assertThat(state.canPlay).isFalse()
        assertThat(state.canPause).isFalse()
    }

    @Test
    fun openVideo_withValidFile_transitionsToLoading_thenReadyOnPrepared() {
        val file = createTempVideoFile()
        val controller = createController()

        controller.openVideo("vid-1", "Test Video", file.absolutePath)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.LOADING)
        assertThat(controller.state.value.targetId).isEqualTo("vid-1")
        assertThat(controller.state.value.title).isEqualTo("Test Video")

        fakeEngine.triggerPrepared(90000L, 1920, 1080)

        val state = controller.state.value
        assertThat(state.phase).isEqualTo(VideoPlaybackPhase.READY)
        assertThat(state.durationMs).isEqualTo(90000L)
        assertThat(state.videoWidth).isEqualTo(1920)
        assertThat(state.videoHeight).isEqualTo(1080)
        assertThat(state.currentPositionMs).isEqualTo(0L)
        assertThat(state.canPlay).isTrue()
        assertThat(state.canPause).isFalse()
    }

    @Test
    fun play_fromReady_transitionsToPlaying() {
        val file = createTempVideoFile()
        val controller = createController()

        controller.openVideo("vid-1", "Test Video", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)

        val started = controller.play()

        assertThat(started).isTrue()
        val state = controller.state.value
        assertThat(state.phase).isEqualTo(VideoPlaybackPhase.PLAYING)
        assertThat(state.canPlay).isFalse()
        assertThat(state.canPause).isTrue()
        assertThat(fakeEngine.isPlaying).isTrue()
    }

    @Test
    fun pause_fromPlaying_transitionsToPaused() {
        val file = createTempVideoFile()
        val controller = createController()

        controller.openVideo("vid-1", "Test Video", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)
        controller.play()

        controller.pause()

        val state = controller.state.value
        assertThat(state.phase).isEqualTo(VideoPlaybackPhase.PAUSED)
        assertThat(state.canPlay).isTrue()
        assertThat(state.canPause).isFalse()
        assertThat(fakeEngine.isPlaying).isFalse()
    }

    @Test
    fun togglePlayPause_cyclesBetweenPlayingAndPaused() {
        val file = createTempVideoFile()
        val controller = createController()

        controller.openVideo("vid-1", "Test Video", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)

        controller.togglePlayPause()
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.PLAYING)

        controller.togglePlayPause()
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.PAUSED)

        controller.togglePlayPause()
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.PLAYING)
    }

    @Test
    fun completion_transitionsToCompleted_withPositionAtDuration() {
        val file = createTempVideoFile()
        val controller = createController()

        controller.openVideo("vid-1", "Test Video", file.absolutePath)
        fakeEngine.triggerPrepared(45000L)
        controller.play()

        fakeEngine.triggerCompletion()

        val state = controller.state.value
        assertThat(state.phase).isEqualTo(VideoPlaybackPhase.COMPLETED)
        assertThat(state.currentPositionMs).isEqualTo(45000L)
        assertThat(state.canPlay).isTrue()
        assertThat(state.canPause).isFalse()
    }

    @Test
    fun completedReplay_whenRewindFails_doesNotStartAndRetainsCompletedPosition() {
        val file = createTempVideoFile()
        val controller = createController()

        controller.openVideo("vid-1", "Test Video", file.absolutePath)
        fakeEngine.triggerPrepared(45000L)
        controller.play()
        fakeEngine.triggerCompletion()

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.COMPLETED)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(45000L)

        // Force seek failure on rewind
        fakeEngine.shouldFailSeek = true

        val started = controller.play()

        assertThat(started).isFalse()
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.COMPLETED)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(45000L)
        assertThat(fakeEngine.isPlaying).isFalse()
    }

    @Test
    fun completedReplay_whenRewindSucceeds_rewindsToZeroAndStartsPlaying() {
        val file = createTempVideoFile()
        val controller = createController()

        controller.openVideo("vid-1", "Test Video", file.absolutePath)
        fakeEngine.triggerPrepared(45000L)
        controller.play()
        fakeEngine.triggerCompletion()

        val started = controller.play()

        assertThat(started).isTrue()
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.PLAYING)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(0L)
        assertThat(fakeEngine.isPlaying).isTrue()
    }

    @Test
    fun error_transitionsToError_withErrorMessage_andReleasesEngine() {
        val file = createTempVideoFile()
        val controller = createController()

        controller.openVideo("vid-1", "Test Video", file.absolutePath)
        fakeEngine.triggerError("Decoder failure")

        val state = controller.state.value
        assertThat(state.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(state.errorMessage).isEqualTo("Decoder failure")
        assertThat(state.canPlay).isFalse()
        assertThat(fakeEngine.releaseCount.get()).isEqualTo(1)
    }

    // ==========================================
    // SESSION GENERATION & STALE CALLBACK TESTS
    // ==========================================

    @Test
    fun sameTarget_openedTwice_latePreparedFromFirstSession_doesNotAffectSecondSession() {
        val file = createTempVideoFile("same.mp4")
        val engine1 = FakeVideoPlaybackEngine()
        val engine2 = FakeVideoPlaybackEngine()

        var callCount = 0
        val controller = VideoPlaybackController(
            engineFactory = {
                callCount++
                if (callCount == 1) engine1 else engine2
            },
            coroutineScope = scope
        )

        controller.openVideo("vid-A", "Track A", file.absolutePath)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.LOADING)

        controller.openVideo("vid-A", "Track A", file.absolutePath)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.LOADING)

        engine1.triggerPrepared(30000L, 1280, 720)

        // Session 2 must remain in LOADING
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.LOADING)
        assertThat(controller.state.value.durationMs).isEqualTo(0L)

        engine2.triggerPrepared(60000L, 1920, 1080)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.READY)
        assertThat(controller.state.value.durationMs).isEqualTo(60000L)
        assertThat(controller.state.value.videoWidth).isEqualTo(1920)
    }

    @Test
    fun differentTarget_opened_latePreparedFromFirstSession_doesNotAffectSecondSession() {
        val fileA = createTempVideoFile("a.mp4")
        val fileB = createTempVideoFile("b.mp4")
        val engine1 = FakeVideoPlaybackEngine()
        val engine2 = FakeVideoPlaybackEngine()

        var callCount = 0
        val controller = VideoPlaybackController(
            engineFactory = {
                callCount++
                if (callCount == 1) engine1 else engine2
            },
            coroutineScope = scope
        )

        controller.openVideo("vid-A", "Video A", fileA.absolutePath)
        controller.openVideo("vid-B", "Video B", fileB.absolutePath)

        engine1.triggerPrepared(30000L)
        assertThat(controller.state.value.targetId).isEqualTo("vid-B")
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.LOADING)
    }

    @Test
    fun staleErrorCallback_doesNotSetNewSessionToError_andDoesNotReleaseNewEngine() {
        val fileA = createTempVideoFile("a.mp4")
        val fileB = createTempVideoFile("b.mp4")
        val engine1 = FakeVideoPlaybackEngine()
        val engine2 = FakeVideoPlaybackEngine()

        var callCount = 0
        val controller = VideoPlaybackController(
            engineFactory = {
                callCount++
                if (callCount == 1) engine1 else engine2
            },
            coroutineScope = scope
        )

        controller.openVideo("vid-A", "Video A", fileA.absolutePath)
        controller.openVideo("vid-B", "Video B", fileB.absolutePath)
        engine2.triggerPrepared(60000L)

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.READY)

        engine1.triggerError("Old error")

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.READY)
        assertThat(controller.state.value.errorMessage).isNull()
        assertThat(engine2.releaseCount.get()).isEqualTo(0)
    }

    @Test
    fun staleCompletionCallback_doesNotSetNewSessionToCompleted() {
        val fileA = createTempVideoFile("a.mp4")
        val fileB = createTempVideoFile("b.mp4")
        val engine1 = FakeVideoPlaybackEngine()
        val engine2 = FakeVideoPlaybackEngine()

        var callCount = 0
        val controller = VideoPlaybackController(
            engineFactory = {
                callCount++
                if (callCount == 1) engine1 else engine2
            },
            coroutineScope = scope
        )

        controller.openVideo("vid-A", "Video A", fileA.absolutePath)
        controller.openVideo("vid-B", "Video B", fileB.absolutePath)
        engine2.triggerPrepared(60000L)

        engine1.triggerCompletion()

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.READY)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(0L)
    }

    @Test
    fun release_invalidatesAllPendingCallbacks() {
        val file = createTempVideoFile("rel.mp4")
        val controller = createController()

        controller.openVideo("vid-1", "Video 1", file.absolutePath)
        controller.release()

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.IDLE)

        fakeEngine.triggerPrepared(50000L)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.IDLE)

        fakeEngine.triggerError("Post-release error")
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.IDLE)

        fakeEngine.triggerCompletion()
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.IDLE)
    }

    // ==========================================
    // FILE VALIDATION AND ACTION-TIME REVALIDATION TESTS
    // ==========================================

    @Test
    fun openVideo_nullOrBlankPath_failsClosedWithErrorMessage() {
        val controller = createController()

        controller.openVideo("vid-null", "Null File", null)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(controller.state.value.errorMessage).contains("missing or blank")

        controller.openVideo("vid-blank", "Blank File", "   ")
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
    }

    @Test
    fun openVideo_missingFile_failsClosedWithErrorMessage() {
        val controller = createController()
        val missing = File(tempFolder.root, "does_not_exist.mp4").absolutePath

        controller.openVideo("vid-missing", "Missing File", missing)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(controller.state.value.errorMessage).contains("not found")
    }

    @Test
    fun openVideo_partOrTmpFile_failsClosedWithErrorMessage() {
        val partFile = createTempVideoFile("downloading.mp4.part")
        val tmpFile = createTempVideoFile("staging.tmp")
        val controller = createController()

        controller.openVideo("vid-part", "Part File", partFile.absolutePath)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(controller.state.value.errorMessage).contains("Incomplete")

        controller.openVideo("vid-tmp", "Tmp File", tmpFile.absolutePath)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(controller.state.value.errorMessage).contains("Incomplete")
    }

    @Test
    fun prepareThrows_releasesEngineAndTransitionsToError() {
        fakeEngine.shouldThrowOnPrepare = true
        val file = createTempVideoFile()
        val controller = createController()

        controller.openVideo("vid-throw", "Throw Track", file.absolutePath)

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(fakeEngine.releaseCount.get()).isGreaterThan(0)
    }

    // ==========================================
    // SEEK AND SPEED CLAMPING TESTS
    // ==========================================

    @Test
    fun seekTo_clampsNegativeToZero_andOverDurationToDuration() {
        val file = createTempVideoFile()
        val controller = createController()

        controller.openVideo("vid-1", "Video", file.absolutePath)
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
        val file = createTempVideoFile()
        val controller = createController()

        controller.openVideo("vid-1", "Video", file.absolutePath)
        fakeEngine.triggerPrepared(50000L)

        controller.setSpeed(0.1f)
        assertThat(controller.state.value.playbackSpeed).isEqualTo(0.5f)

        controller.setSpeed(0.5f)
        assertThat(controller.state.value.playbackSpeed).isEqualTo(0.5f)

        controller.setSpeed(1.0f)
        assertThat(controller.state.value.playbackSpeed).isEqualTo(1.0f)

        controller.setSpeed(1.5f)
        assertThat(controller.state.value.playbackSpeed).isEqualTo(1.5f)

        controller.setSpeed(2.0f)
        assertThat(controller.state.value.playbackSpeed).isEqualTo(2.0f)

        controller.setSpeed(3.5f)
        assertThat(controller.state.value.playbackSpeed).isEqualTo(2.0f)
    }

    // ==========================================
    // SWITCHING AND IDEMPOTENT RELEASE TESTS
    // ==========================================

    @Test
    fun switchingVideo_releasesPreviousSessionExactlyOnce() {
        val fileA = createTempVideoFile("a.mp4")
        val fileB = createTempVideoFile("b.mp4")

        val engineA = FakeVideoPlaybackEngine()
        val engineB = FakeVideoPlaybackEngine()

        var callCount = 0
        val controller = VideoPlaybackController(
            engineFactory = {
                callCount++
                if (callCount == 1) engineA else engineB
            },
            coroutineScope = scope
        )

        controller.openVideo("vid-A", "Video A", fileA.absolutePath)
        engineA.triggerPrepared(30000L)
        assertThat(controller.state.value.targetId).isEqualTo("vid-A")

        controller.openVideo("vid-B", "Video B", fileB.absolutePath)
        engineB.triggerPrepared(40000L)

        assertThat(engineA.releaseCount.get()).isEqualTo(1)
        assertThat(controller.state.value.targetId).isEqualTo("vid-B")
        assertThat(controller.state.value.durationMs).isEqualTo(40000L)
    }

    @Test
    fun release_isIdempotentAndSafeToCallMultipleTimes() {
        val file = createTempVideoFile()
        val controller = createController()

        controller.openVideo("vid-1", "Video", file.absolutePath)
        fakeEngine.triggerPrepared(50000L)

        controller.release()
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.IDLE)

        controller.release()
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.IDLE)
    }

    @Test
    fun realPosition_comesFromEngine_neverIncrementedSynthetically() {
        val file = createTempVideoFile()
        val controller = createController()

        controller.openVideo("vid-1", "Video", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)
        controller.play()

        fakeEngine.fakeCurrentPosition = 12345L

        runBlocking {
            delay(300)
        }

        assertThat(controller.state.value.currentPositionMs).isEqualTo(12345L)
    }
}
