package com.personaltool.app.video

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.model.media.DownloadStatus
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
    var onActivityChangedCallback: ((VideoPlaybackActivity) -> Unit)? = null
    var onPositionDiscontinuityCallback: ((Long) -> Unit)? = null

    var shouldThrowOnPrepare = false
    var shouldFailPlay = false
    var shouldFailPause = false
    var shouldFailSeek = false
    var autoConfirmActivityOnRequest = true
    var autoConfirmSeekOnRequest = true

    override fun prepare(
        filePath: String,
        onPrepared: (durationMs: Long, width: Int, height: Int) -> Unit,
        onError: (errorMessage: String) -> Unit,
        onCompletion: () -> Unit,
        onActivityChanged: (activity: VideoPlaybackActivity) -> Unit,
        onPositionDiscontinuity: (confirmedPositionMs: Long) -> Unit
    ) {
        if (shouldThrowOnPrepare) {
            throw IllegalStateException("Forced prepare failure")
        }
        onPreparedCallback = onPrepared
        onErrorCallback = onError
        onCompletionCallback = onCompletion
        onActivityChangedCallback = onActivityChanged
        onPositionDiscontinuityCallback = onPositionDiscontinuity
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
        onActivityChangedCallback?.invoke(VideoPlaybackActivity.ENDED)
        onCompletionCallback?.invoke()
    }

    fun triggerActivity(activity: VideoPlaybackActivity) {
        isPlaying = (activity == VideoPlaybackActivity.PLAYING)
        onActivityChangedCallback?.invoke(activity)
    }

    fun triggerPositionDiscontinuity(positionMs: Long) {
        fakeCurrentPosition = positionMs
        onPositionDiscontinuityCallback?.invoke(positionMs)
    }

    override fun requestPlay(): Boolean {
        if (shouldFailPlay) return false
        if (autoConfirmActivityOnRequest) {
            isPlaying = true
            onActivityChangedCallback?.invoke(VideoPlaybackActivity.PLAYING)
        }
        return true
    }

    override fun requestPause(): Boolean {
        if (shouldFailPause) return false
        if (autoConfirmActivityOnRequest) {
            isPlaying = false
            onActivityChangedCallback?.invoke(VideoPlaybackActivity.PAUSED)
        }
        return true
    }

    override fun requestSeek(positionMs: Long): Boolean {
        if (shouldFailSeek) return false
        val clamped = positionMs.coerceIn(0L, fakeDuration)
        if (autoConfirmSeekOnRequest) {
            fakeCurrentPosition = clamped
            onPositionDiscontinuityCallback?.invoke(clamped)
        }
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

    private fun createValidMp4File(name: String = "test_video.mp4", size: Int = 8192): File {
        val file = tempFolder.newFile(name)
        val data = ByteArray(size)
        // ISO-BMFF header: length 0x20, 'ftyp', 'isom'
        data[0] = 0x00
        data[1] = 0x00
        data[2] = 0x00
        data[3] = 0x20
        data[4] = 'f'.code.toByte()
        data[5] = 't'.code.toByte()
        data[6] = 'y'.code.toByte()
        data[7] = 'p'.code.toByte()
        data[8] = 'i'.code.toByte()
        data[9] = 's'.code.toByte()
        data[10] = 'o'.code.toByte()
        data[11] = 'm'.code.toByte()
        FileOutputStream(file).use { it.write(data) }
        return file
    }

    private fun createRandomByteFile(name: String = "random_corrupt.mp4", size: Int = 8192): File {
        val file = tempFolder.newFile(name)
        FileOutputStream(file).use { fos ->
            fos.write(ByteArray(size) { 0x22 })
        }
        return file
    }

    // ==========================================
    // BLOCKER 01: AUTHORITATIVE PLAYING STATE TESTS
    // ==========================================

    @Test
    fun play_intentAccepted_butEngineHasNotEmittedPlaying_doesNotClaimPlaying() {
        val file = createValidMp4File()
        val controller = createController()

        controller.openVideo("vid-1", "Test Video", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)

        // Disable auto-confirm to simulate asynchronous engine/Media3 playing delay
        fakeEngine.autoConfirmActivityOnRequest = false

        val accepted = controller.play()

        assertThat(accepted).isTrue()
        // Controller must NOT claim PLAYING yet
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.READY)
        assertThat(controller.state.value.canPlay).isTrue()

        // When engine authoritatively emits PLAYING
        fakeEngine.triggerActivity(VideoPlaybackActivity.PLAYING)

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.PLAYING)
        assertThat(controller.state.value.canPlay).isFalse()
        assertThat(controller.state.value.canPause).isTrue()
    }

    @Test
    fun playing_whenEngineEmitsPaused_controllerTransitionsToPaused_andStopsPolling() {
        val file = createValidMp4File()
        val controller = createController()

        controller.openVideo("vid-1", "Test Video", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)
        controller.play()

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.PLAYING)

        // Engine emits PAUSED (e.g. system focus loss or pause)
        fakeEngine.fakeCurrentPosition = 15000L
        fakeEngine.triggerActivity(VideoPlaybackActivity.PAUSED)

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.PAUSED)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(15000L)
        assertThat(controller.state.value.canPlay).isTrue()
        assertThat(controller.state.value.canPause).isFalse()
    }

    @Test
    fun playing_whenEngineEmitsBuffering_controllerTransitionsToLoading_andStopsPolling() {
        val file = createValidMp4File()
        val controller = createController()

        controller.openVideo("vid-1", "Test Video", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)
        controller.play()

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.PLAYING)

        // Engine emits BUFFERING
        fakeEngine.triggerActivity(VideoPlaybackActivity.BUFFERING)

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.LOADING)

        // Engine resumes PLAYING
        fakeEngine.triggerActivity(VideoPlaybackActivity.PLAYING)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.PLAYING)
    }

    // ==========================================
    // BLOCKER 02: ASYNCHRONOUS SEEK & REPLAY CONFIRMATION TESTS
    // ==========================================

    @Test
    fun seekTo_doesNotUpdatePositionImmediately_untilEngineConfirmsDiscontinuity() {
        val file = createValidMp4File()
        val controller = createController()

        controller.openVideo("vid-1", "Test Video", file.absolutePath)
        fakeEngine.triggerPrepared(60000L)

        // Disable auto-confirm seek
        fakeEngine.autoConfirmSeekOnRequest = false

        val requested = controller.seekTo(30000L)
        assertThat(requested).isTrue()

        // Position MUST NOT have changed yet
        assertThat(controller.state.value.currentPositionMs).isEqualTo(0L)

        // Engine confirms seek to 30000ms
        fakeEngine.triggerPositionDiscontinuity(30000L)

        assertThat(controller.state.value.currentPositionMs).isEqualTo(30000L)
    }

    @Test
    fun completedReplay_waitsForRewindConfirmation_beforeIssuingPlay() {
        val file = createValidMp4File()
        val controller = createController()

        controller.openVideo("vid-1", "Test Video", file.absolutePath)
        fakeEngine.triggerPrepared(45000L)
        controller.play()
        fakeEngine.triggerCompletion()

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.COMPLETED)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(45000L)

        // Disable auto-confirm seek and play
        fakeEngine.autoConfirmSeekOnRequest = false
        fakeEngine.autoConfirmActivityOnRequest = false

        val playAccepted = controller.play()
        assertThat(playAccepted).isTrue()

        // Must still be COMPLETED and at end until rewind is confirmed
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.COMPLETED)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(45000L)

        // Engine confirms rewind to 0
        fakeEngine.triggerPositionDiscontinuity(0L)

        // Controller updates position to 0 and state to READY
        assertThat(controller.state.value.currentPositionMs).isEqualTo(0L)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.READY)

        // Engine then starts playing and emits PLAYING
        fakeEngine.triggerActivity(VideoPlaybackActivity.PLAYING)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.PLAYING)
        assertThat(controller.state.value.currentPositionMs).isEqualTo(0L)
    }

    @Test
    fun completedReplay_whenRewindFails_doesNotStartAndRetainsCompletedPosition() {
        val file = createValidMp4File()
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

    // ==========================================
    // BLOCKER 03: ACTION-TIME PREFLIGHT VALIDATION TESTS
    // ==========================================

    @Test
    fun openVideo_readableRandomBytesMp4_failsClosedWithInvalidMedia() {
        val corruptFile = createRandomByteFile("corrupt.mp4", size = 4096)
        val controller = createController()

        controller.openVideo("vid-corrupt", "Corrupt File", corruptFile.absolutePath)

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(controller.state.value.errorMessage).contains("not a valid media container")
        assertThat(fakeEngine.isPrepared).isFalse()
    }

    @Test
    fun openVideo_expectedSizeMismatch_failsClosedWithSizeMismatch() {
        val file = createValidMp4File("mismatch.mp4", size = 2048)
        val controller = createController()

        controller.openVideo(
            targetId = "vid-mismatch",
            title = "Size Mismatch File",
            filePath = file.absolutePath,
            expectedSizeBytes = 8192L
        )

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(controller.state.value.errorMessage).contains("size mismatch")
        assertThat(fakeEngine.isPrepared).isFalse()
    }

    @Test
    fun openVideo_downloadStatusDownloading_failsClosedWithNotReady() {
        val file = createValidMp4File("downloading.mp4")
        val controller = createController()

        controller.openVideo(
            targetId = "vid-downloading",
            title = "Downloading File",
            filePath = file.absolutePath,
            downloadStatus = DownloadStatus.DOWNLOADING
        )

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(controller.state.value.errorMessage).contains("not ready for playback")
        assertThat(fakeEngine.isPrepared).isFalse()
    }

    @Test
    fun openVideo_partOrTmpFile_failsClosedWithInvalidMedia() {
        val partFile = createValidMp4File("downloading.mp4.part")
        val tmpFile = createValidMp4File("staging.tmp")
        val controller = createController()

        controller.openVideo("vid-part", "Part File", partFile.absolutePath)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(controller.state.value.errorMessage).contains("corrupt, incomplete, or not a valid media container")

        controller.openVideo("vid-tmp", "Tmp File", tmpFile.absolutePath)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(controller.state.value.errorMessage).contains("corrupt, incomplete, or not a valid media container")
    }

    @Test
    fun openVideo_missingFile_failsClosedWithNotFound() {
        val controller = createController()
        val missing = File(tempFolder.root, "does_not_exist.mp4").absolutePath

        controller.openVideo("vid-missing", "Missing File", missing)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(controller.state.value.errorMessage).contains("not found")
    }

    @Test
    fun openVideo_nullOrBlankPath_failsClosedWithMissingOrBlank() {
        val controller = createController()

        controller.openVideo("vid-null", "Null File", null)
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(controller.state.value.errorMessage).contains("missing or blank")

        controller.openVideo("vid-blank", "Blank File", "   ")
        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
    }

    // ==========================================
    // STATE TRANSITION & LIFECYCLE TESTS
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
        val file = createValidMp4File()
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
    fun pause_fromPlaying_transitionsToPaused() {
        val file = createValidMp4File()
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
        val file = createValidMp4File()
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
        val file = createValidMp4File()
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
    fun error_transitionsToError_withErrorMessage_andReleasesEngine() {
        val file = createValidMp4File()
        val controller = createController()

        controller.openVideo("vid-1", "Test Video", file.absolutePath)
        fakeEngine.triggerError("Decoder failure")

        val state = controller.state.value
        assertThat(state.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(state.errorMessage).isEqualTo("Decoder failure")
        assertThat(state.canPlay).isFalse()
        assertThat(fakeEngine.releaseCount.get()).isEqualTo(1)
    }

    @Test
    fun prepareThrows_releasesEngineAndTransitionsToError() {
        fakeEngine.shouldThrowOnPrepare = true
        val file = createValidMp4File()
        val controller = createController()

        controller.openVideo("vid-throw", "Throw Track", file.absolutePath)

        assertThat(controller.state.value.phase).isEqualTo(VideoPlaybackPhase.ERROR)
        assertThat(fakeEngine.releaseCount.get()).isGreaterThan(0)
    }

    // ==========================================
    // SESSION GENERATION & STALE CALLBACK TESTS
    // ==========================================

    @Test
    fun sameTarget_openedTwice_latePreparedFromFirstSession_doesNotAffectSecondSession() {
        val file = createValidMp4File("same.mp4")
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
        val fileA = createValidMp4File("a.mp4")
        val fileB = createValidMp4File("b.mp4")
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
        val fileA = createValidMp4File("a.mp4")
        val fileB = createValidMp4File("b.mp4")
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
        val fileA = createValidMp4File("a.mp4")
        val fileB = createValidMp4File("b.mp4")
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
        val file = createValidMp4File("rel.mp4")
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
    // SEEK AND SPEED CLAMPING TESTS
    // ==========================================

    @Test
    fun seekTo_clampsNegativeToZero_andOverDurationToDuration() {
        val file = createValidMp4File()
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
        val file = createValidMp4File()
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
        val fileA = createValidMp4File("a.mp4")
        val fileB = createValidMp4File("b.mp4")

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
        val file = createValidMp4File()
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
        val file = createValidMp4File()
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
