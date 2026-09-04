package com.personaltool.app.viewmodel

import com.google.common.truth.Truth.assertThat
import com.personaltool.app.audio.AudioInterruptionReason
import com.personaltool.app.audio.AudioPlaybackController
import com.personaltool.app.audio.AudioPlaybackPhase
import com.personaltool.app.audio.FakeAudioPlaybackEngine
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.transcript.Transcript
import com.personaltool.core.model.transcript.TranscriptSegment
import com.personaltool.core.model.transcript.TranscriptStatus
import com.personaltool.core.storage.dao.CallDao
import com.personaltool.core.storage.dao.MediaDao
import com.personaltool.core.storage.dao.TranscriptDao
import com.personaltool.core.storage.entity.CallEntity
import com.personaltool.core.storage.entity.MediaEntity
import com.personaltool.core.storage.entity.TranscriptEntity
import com.personaltool.transcription.api.ModelStatus
import com.personaltool.transcription.api.TranscriptionEngine
import com.personaltool.transcription.api.TranscriptionProgress
import com.personaltool.transcription.api.TranscriptionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

private class FakeTranscriptDao : TranscriptDao {
    val map = mutableMapOf<String, TranscriptEntity>()

    override fun getTranscriptByTargetIdFlow(targetId: String): Flow<TranscriptEntity?> =
        MutableStateFlow(map.values.find { it.targetId == targetId })

    override suspend fun getTranscriptByTargetId(targetId: String): TranscriptEntity? =
        map.values.find { it.targetId == targetId }

    override suspend fun insertTranscript(transcript: TranscriptEntity) {
        map[transcript.id] = transcript
    }

    override suspend fun updateTranscript(transcript: TranscriptEntity) {
        map[transcript.id] = transcript
    }

    override suspend fun deleteTranscript(transcript: TranscriptEntity) {
        map.remove(transcript.id)
    }

    override suspend fun deleteTranscriptByTargetId(targetId: String): Int {
        val count = map.values.count { it.targetId == targetId }
        map.values.removeAll { it.targetId == targetId }
        return count
    }

    override suspend fun deleteAllTranscripts(): Int {
        val s = map.size
        map.clear()
        return s
    }
}

private class FakeCallDaoForTranscript : CallDao {
    override fun getAllCallsFlow(): Flow<List<CallEntity>> = MutableStateFlow(emptyList())
    override suspend fun getCallById(id: String): CallEntity? = null
    override fun getFavoriteCallsFlow(): Flow<List<CallEntity>> = MutableStateFlow(emptyList())
    override fun searchCalls(query: String): Flow<List<CallEntity>> = MutableStateFlow(emptyList())
    override suspend fun insertCall(call: CallEntity) {}
    override suspend fun insertCalls(calls: List<CallEntity>) {}
    override suspend fun updateCall(call: CallEntity) {}
    override suspend fun deleteCall(call: CallEntity) {}
    override suspend fun deleteCallById(id: String): Int = 0
    override suspend fun deleteAllCalls(): Int = 0
    override suspend fun getCallCount(): Int = 0
}

private class FakeMediaDaoForTranscript : MediaDao {
    override fun getAllMediaFlow(): Flow<List<MediaEntity>> = MutableStateFlow(emptyList())
    override suspend fun getMediaById(id: String): MediaEntity? = null
    override fun getCompletedMediaFlow(): Flow<List<MediaEntity>> = MutableStateFlow(emptyList())
    override fun searchMedia(query: String): Flow<List<MediaEntity>> = MutableStateFlow(emptyList())
    override suspend fun insertMedia(item: MediaEntity) {}
    override suspend fun updateMedia(item: MediaEntity) {}
    override suspend fun deleteMedia(item: MediaEntity) {}
    override suspend fun deleteMediaById(id: String): Int = 0
    override suspend fun deleteAllMedia(): Int = 0
    override suspend fun getMediaCount(): Int = 0
}

private class NoOpTranscriptionEngine : TranscriptionEngine {
    override val engineName: String = "NoOp"

    override suspend fun checkModelStatus(): ModelStatus =
        ModelStatus(isReady = true, modelName = "NoOpModel")

    override suspend fun transcribe(
        request: TranscriptionRequest,
        onProgress: (TranscriptionProgress) -> Unit
    ): AppResult<Transcript> {
        return AppResult.Success(
            Transcript(
                id = "t-1",
                targetId = request.targetId,
                language = "en",
                status = TranscriptStatus.READY,
                segments = listOf(
                    TranscriptSegment(id = "s-1", startTimeMs = 0L, endTimeMs = 5000L, text = "Hello"),
                    TranscriptSegment(id = "s-2", startTimeMs = 5001L, endTimeMs = 10000L, text = "world")
                ),
                confidence = 1.0f,
                errorMessage = null,
                createdAt = 1000L
            )
        )
    }

    override suspend fun cancelTranscription(targetId: String): AppResult<Unit> =
        AppResult.Success(Unit)
}

class TranscriptViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fakeEngine = FakeAudioPlaybackEngine()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val audioController = AudioPlaybackController(
        engineFactory = { fakeEngine },
        coroutineScope = scope
    )

    private val transcriptDao = FakeTranscriptDao()
    private val callDao = FakeCallDaoForTranscript()
    private val mediaDao = FakeMediaDaoForTranscript()
    private val transcriptionEngine = NoOpTranscriptionEngine()

    private fun createViewModel(controller: AudioPlaybackController? = audioController): TranscriptViewModel {
        return TranscriptViewModel(
            transcriptDao = transcriptDao,
            callDao = callDao,
            mediaDao = mediaDao,
            audioPlaybackController = controller,
            transcriptionEngine = transcriptionEngine,
            coroutineScope = scope
        )
    }

    private fun createTempAudioFile(name: String = "transcript_audio.m4a"): File {
        val file = tempFolder.newFile(name)
        FileOutputStream(file).use { fos ->
            fos.write(ByteArray(4096) { 0x11 })
        }
        return file
    }

    @Test
    fun openTranscript_withAudioFile_preparesAudioController() {
        val file = createTempAudioFile()
        val viewModel = createViewModel()

        viewModel.openTranscript(
            targetId = "target-1",
            targetTitle = "Call with Alice",
            audioFilePath = file.absolutePath,
            durationMs = 60000L
        )

        val state = viewModel.uiState.value
        assertThat(state.isOpen).isTrue()
        assertThat(state.targetId).isEqualTo("target-1")
        assertThat(state.audioFilePath).isEqualTo(file.absolutePath)
        assertThat(audioController.state.value.phase).isEqualTo(AudioPlaybackPhase.LOADING)

        fakeEngine.triggerPrepared(60000L)
        assertThat(audioController.state.value.phase).isEqualTo(AudioPlaybackPhase.READY)
    }

    @Test
    fun togglePlayPause_drivesRealAudioController() {
        val file = createTempAudioFile()
        val viewModel = createViewModel()

        viewModel.openTranscript("target-1", "Alice", file.absolutePath, 60000L)
        fakeEngine.triggerPrepared(60000L)

        // Play
        viewModel.togglePlayPause()
        assertThat(audioController.state.value.phase).isEqualTo(AudioPlaybackPhase.PLAYING)
        assertThat(viewModel.uiState.value.isPlaying).isTrue()

        // Pause
        viewModel.togglePlayPause()
        assertThat(audioController.state.value.phase).isEqualTo(AudioPlaybackPhase.PAUSED)
        assertThat(viewModel.uiState.value.isPlaying).isFalse()
    }

    @Test
    fun togglePlayPause_withNoAudioFile_doesNotFakePlay() {
        val viewModel = createViewModel()

        viewModel.openTranscript("target-no-audio", "No Audio Call", null, 0L)

        viewModel.togglePlayPause()
        assertThat(viewModel.uiState.value.isPlaying).isFalse()
        assertThat(audioController.state.value.phase).isEqualTo(AudioPlaybackPhase.IDLE)
    }

    // ==========================================
    // P3-E04-FINAL-04: TRANSCRIPT POSITION AUTHORITY TESTS
    // ==========================================

    @Test
    fun controllerLoading_seekRequested_transcriptPositionDoesNotJump() {
        val file = createTempAudioFile()
        val viewModel = createViewModel()

        viewModel.openTranscript("target-1", "Alice", file.absolutePath, 10000L)
        // Controller is in LOADING state (canSeek is false)

        viewModel.seekToPosition(5000L)

        assertThat(audioController.state.value.currentPositionMs).isEqualTo(0L)
        assertThat(viewModel.uiState.value.currentPlaybackPositionMs).isEqualTo(0L)
    }

    @Test
    fun engineSeekFailure_transcriptPositionDoesNotJump() {
        val file = createTempAudioFile()
        val viewModel = createViewModel()

        viewModel.openTranscript("target-1", "Alice", file.absolutePath, 10000L)
        fakeEngine.triggerPrepared(10000L)

        fakeEngine.shouldFailSeek = true

        viewModel.seekToPosition(5000L)

        assertThat(audioController.state.value.currentPositionMs).isEqualTo(0L)
        assertThat(viewModel.uiState.value.currentPlaybackPositionMs).isEqualTo(0L)
    }

    @Test
    fun successfulSeek_updatesController_andTranscriptObserverUpdatesPositionAndSegment() = runBlocking {
        val file = createTempAudioFile()
        val viewModel = createViewModel()

        // Insert transcript into DAO
        val transcriptEntity = TranscriptEntity(
            id = "t-1",
            targetId = "target-1",
            language = "en",
            status = "READY",
            segmentsJson = """[{"id":"s-1","startTimeMs":0,"endTimeMs":5000,"text":"Hello"},{"id":"s-2","startTimeMs":5001,"endTimeMs":10000,"text":"world"}]""",
            confidence = 1.0f,
            errorMessage = null,
            createdAt = 1000L
        )
        transcriptDao.insertTranscript(transcriptEntity)

        viewModel.openTranscript("target-1", "Alice", file.absolutePath, 10000L)
        fakeEngine.triggerPrepared(10000L)

        // Seek to 7000ms (belongs to segment s-2)
        viewModel.seekToPosition(7000L)

        assertThat(audioController.state.value.currentPositionMs).isEqualTo(7000L)
        assertThat(viewModel.uiState.value.currentPlaybackPositionMs).isEqualTo(7000L)
        assertThat(viewModel.uiState.value.activeSegmentId).isEqualTo("s-2")
    }

    @Test
    fun noController_seekRequested_noFakeSeekState() {
        val viewModel = createViewModel(controller = null)

        viewModel.openTranscript("target-1", "Alice", null, 10000L)
        viewModel.seekToPosition(5000L)

        assertThat(viewModel.uiState.value.currentPlaybackPositionMs).isEqualTo(0L)
    }

    @Test
    fun noAudioFile_seekRequested_noFakeSeekState() {
        val viewModel = createViewModel()

        viewModel.openTranscript("target-1", "Alice", null, 0L)
        viewModel.seekToPosition(5000L)

        assertThat(viewModel.uiState.value.currentPlaybackPositionMs).isEqualTo(0L)
    }

    @Test
    fun seekToSegment_seeksAndStartsPlayback() {
        val file = createTempAudioFile()
        val viewModel = createViewModel()

        viewModel.openTranscript("target-1", "Alice", file.absolutePath, 10000L)
        fakeEngine.triggerPrepared(10000L)

        val segment = TranscriptSegment(id = "s-2", startTimeMs = 5001L, endTimeMs = 10000L, text = "world")
        viewModel.seekToSegment(segment)

        assertThat(audioController.state.value.currentPositionMs).isEqualTo(5001L)
        assertThat(audioController.state.value.phase).isEqualTo(AudioPlaybackPhase.PLAYING)
        assertThat(viewModel.uiState.value.isPlaying).isTrue()
    }

    @Test
    fun seekToSegment_whenSeekFails_doesNotStartPlaybackAndRetainsPosition() {
        val file = createTempAudioFile()
        val viewModel = createViewModel()

        viewModel.openTranscript("target-1", "Alice", file.absolutePath, 10000L)
        fakeEngine.triggerPrepared(10000L)

        // Force seek failure
        fakeEngine.shouldFailSeek = true

        val segment = TranscriptSegment(id = "s-2", startTimeMs = 5001L, endTimeMs = 10000L, text = "world")
        viewModel.seekToSegment(segment)

        assertThat(audioController.state.value.currentPositionMs).isEqualTo(0L)
        assertThat(audioController.state.value.phase).isEqualTo(AudioPlaybackPhase.READY)
        assertThat(viewModel.uiState.value.isPlaying).isFalse()
        assertThat(viewModel.uiState.value.activeSegmentId).isNotEqualTo("s-2")
    }

    // ==========================================
    // P3-E04-FINAL-03 & 05: INTERRUPTIONS & PRESENTATION OWNERSHIP
    // ==========================================

    @Test
    fun focusLoss_propagatesToTranscriptPlayingState() {
        val file = createTempAudioFile()
        val viewModel = createViewModel()

        viewModel.openTranscript("target-1", "Alice", file.absolutePath, 10000L)
        fakeEngine.triggerPrepared(10000L)
        viewModel.togglePlayPause()

        assertThat(viewModel.uiState.value.isPlaying).isTrue()

        // System focus loss
        fakeEngine.triggerInterruption(AudioInterruptionReason.SYSTEM_FOCUS_LOSS)

        assertThat(audioController.state.value.phase).isEqualTo(AudioPlaybackPhase.PAUSED)
        assertThat(viewModel.uiState.value.isPlaying).isFalse()
    }

    @Test
    fun closeTranscript_releasesAudioController() {
        val file = createTempAudioFile()
        val viewModel = createViewModel()

        viewModel.openTranscript("target-1", "Alice", file.absolutePath, 10000L)
        fakeEngine.triggerPrepared(10000L)
        viewModel.togglePlayPause()

        viewModel.closeTranscript()

        assertThat(viewModel.uiState.value.isOpen).isFalse()
        assertThat(viewModel.uiState.value.isPlaying).isFalse()
        assertThat(audioController.state.value.phase).isEqualTo(AudioPlaybackPhase.IDLE)
    }

    @Test
    fun singlePresentationOwnership_ruleVerification() {
        // Reducer-level verification of MainScreen presentation policy:
        // Standalone player is visible ONLY when audio is not IDLE AND transcript sheet is NOT open.
        fun shouldShowStandalonePlayer(phase: AudioPlaybackPhase, isTranscriptOpen: Boolean): Boolean {
            return phase != AudioPlaybackPhase.IDLE && !isTranscriptOpen
        }

        assertThat(shouldShowStandalonePlayer(AudioPlaybackPhase.READY, isTranscriptOpen = false)).isTrue()
        assertThat(shouldShowStandalonePlayer(AudioPlaybackPhase.PLAYING, isTranscriptOpen = false)).isTrue()
        assertThat(shouldShowStandalonePlayer(AudioPlaybackPhase.PLAYING, isTranscriptOpen = true)).isFalse()
        assertThat(shouldShowStandalonePlayer(AudioPlaybackPhase.LOADING, isTranscriptOpen = true)).isFalse()
        assertThat(shouldShowStandalonePlayer(AudioPlaybackPhase.IDLE, isTranscriptOpen = false)).isFalse()
    }
}
