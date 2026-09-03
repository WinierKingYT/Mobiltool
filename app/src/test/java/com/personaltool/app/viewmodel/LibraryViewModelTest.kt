package com.personaltool.app.viewmodel

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.model.call.CallDirection
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.media.DownloadStatus
import com.personaltool.core.model.media.MediaItem
import com.personaltool.core.model.media.MediaSource
import com.personaltool.core.model.media.MediaType
import com.personaltool.core.storage.dao.CallDao
import com.personaltool.core.storage.dao.MediaDao
import com.personaltool.core.storage.entity.CallEntity
import com.personaltool.core.storage.entity.MediaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

private class FakeCallDao : CallDao {
    val callsFlow = MutableStateFlow<List<CallEntity>>(emptyList())

    override fun getAllCallsFlow(): Flow<List<CallEntity>> = callsFlow
    override suspend fun getCallById(id: String): CallEntity? = callsFlow.value.find { it.id == id }
    override fun getFavoriteCallsFlow(): Flow<List<CallEntity>> = MutableStateFlow(callsFlow.value.filter { it.isFavorite })
    override fun searchCalls(query: String): Flow<List<CallEntity>> = MutableStateFlow(callsFlow.value)
    override suspend fun insertCall(call: CallEntity) { callsFlow.value = callsFlow.value + call }
    override suspend fun insertCalls(calls: List<CallEntity>) { callsFlow.value = callsFlow.value + calls }
    override suspend fun updateCall(call: CallEntity) {}
    override suspend fun deleteCall(call: CallEntity) {}
    override suspend fun deleteCallById(id: String): Int = 0
    override suspend fun deleteAllCalls(): Int = 0
    override suspend fun getCallCount(): Int = callsFlow.value.size
}

private class FakeMediaDao : MediaDao {
    val mediaFlow = MutableStateFlow<List<MediaEntity>>(emptyList())

    override fun getAllMediaFlow(): Flow<List<MediaEntity>> = mediaFlow
    override suspend fun getMediaById(id: String): MediaEntity? = mediaFlow.value.find { it.id == id }
    override fun getCompletedMediaFlow(): Flow<List<MediaEntity>> = MutableStateFlow(mediaFlow.value.filter { it.downloadStatus == "COMPLETED" })
    override fun searchMedia(query: String): Flow<List<MediaEntity>> = MutableStateFlow(mediaFlow.value)
    override suspend fun insertMedia(item: MediaEntity) { mediaFlow.value = mediaFlow.value + item }
    override suspend fun updateMedia(item: MediaEntity) {}
    override suspend fun deleteMedia(item: MediaEntity) {}
    override suspend fun deleteMediaById(id: String): Int = 0
    override suspend fun deleteAllMedia(): Int = 0
    override suspend fun getMediaCount(): Int = mediaFlow.value.size
}

private class TestVaultItemEvaluator : VaultItemEvaluator {
    override fun evaluateCall(session: CallSession): VaultItem.Call {
        val audioPath = session.audioFilePath
        val isAvailable = audioPath != null && !audioPath.contains("missing")
        return VaultItem.Call(
            session = session,
            fileState = if (isAvailable) VaultFileState.AVAILABLE else VaultFileState.MISSING,
            primaryAction = if (isAvailable) VaultPrimaryAction.PLAY_AUDIO else if (session.hasTranscript) VaultPrimaryAction.OPEN_TRANSCRIPT else VaultPrimaryAction.UNAVAILABLE,
            availableSizeBytes = if (isAvailable) session.fileSizeBytes else 0L
        )
    }

    override fun evaluateMedia(item: MediaItem): VaultItem.Media {
        val localPath = item.localFilePath
        val isAvailable = localPath != null && !localPath.contains("missing") && item.downloadStatus == DownloadStatus.COMPLETED
        return VaultItem.Media(
            item = item,
            fileState = if (isAvailable) VaultFileState.AVAILABLE else VaultFileState.MISSING,
            primaryAction = if (isAvailable) (if (item.mediaType == MediaType.VIDEO) VaultPrimaryAction.PLAY_VIDEO else VaultPrimaryAction.PLAY_AUDIO) else VaultPrimaryAction.UNAVAILABLE,
            availableSizeBytes = if (isAvailable) item.fileSizeBytes else 0L
        )
    }
}

class LibraryViewModelTest {

    private val fakeCallDao = FakeCallDao()
    private val fakeMediaDao = FakeMediaDao()
    private val evaluator = TestVaultItemEvaluator()

    private fun createViewModel(scope: CoroutineScope): LibraryViewModel {
        return LibraryViewModel(
            callDao = fakeCallDao,
            mediaDao = fakeMediaDao,
            evaluator = evaluator,
            coroutineScope = scope
        )
    }

    // ==========================================
    // MULTI-CRITERIA SEARCH TESTS
    // ==========================================

    @Test
    fun search_byContactName_matchesCall() {
        val call = CallEntity.fromDomain(
            CallSession(
                id = "call-1",
                phoneNumber = "+1234567890",
                contactName = "Alice Smith",
                direction = CallDirection.INCOMING,
                startTimeEpochMs = 1000L,
                createdAt = 1000L
            )
        )
        val state = LibraryViewModel.buildUiState(
            callsEntities = listOf(call),
            mediaEntities = emptyList(),
            filter = LibraryFilter.ALL,
            rawQuery = "alice",
            evaluator = evaluator
        )
        assertThat(state.items).hasSize(1)
        assertThat(state.items.first().id).isEqualTo("call-1")
    }

    @Test
    fun search_byPhoneNumber_matchesCall() {
        val call = CallEntity.fromDomain(
            CallSession(
                id = "call-2",
                phoneNumber = "+905551112233",
                contactName = null,
                direction = CallDirection.OUTGOING,
                startTimeEpochMs = 1000L,
                createdAt = 1000L
            )
        )
        val state = LibraryViewModel.buildUiState(
            callsEntities = listOf(call),
            mediaEntities = emptyList(),
            filter = LibraryFilter.ALL,
            rawQuery = "555111",
            evaluator = evaluator
        )
        assertThat(state.items).hasSize(1)
        assertThat(state.items.first().id).isEqualTo("call-2")
    }

    @Test
    fun search_byCallDirection_matchesCall() {
        val incomingCall = CallEntity.fromDomain(
            CallSession(
                id = "call-in",
                phoneNumber = "+111",
                direction = CallDirection.INCOMING,
                startTimeEpochMs = 1000L,
                createdAt = 1000L
            )
        )
        val outgoingCall = CallEntity.fromDomain(
            CallSession(
                id = "call-out",
                phoneNumber = "+222",
                direction = CallDirection.OUTGOING,
                startTimeEpochMs = 2000L,
                createdAt = 2000L
            )
        )
        val state = LibraryViewModel.buildUiState(
            callsEntities = listOf(incomingCall, outgoingCall),
            mediaEntities = emptyList(),
            filter = LibraryFilter.ALL,
            rawQuery = "INCOMING",
            evaluator = evaluator
        )
        assertThat(state.items).hasSize(1)
        assertThat(state.items.first().id).isEqualTo("call-in")
    }

    @Test
    fun search_byMediaTitle_matchesMedia() {
        val media = MediaEntity.fromDomain(
            MediaItem(
                id = "media-1",
                sourceUrl = "https://youtube.com/watch?v=123",
                title = "Big Buck Bunny 4K",
                createdAt = 1000L
            )
        )
        val state = LibraryViewModel.buildUiState(
            callsEntities = emptyList(),
            mediaEntities = listOf(media),
            filter = LibraryFilter.ALL,
            rawQuery = "bunny",
            evaluator = evaluator
        )
        assertThat(state.items).hasSize(1)
        assertThat(state.items.first().id).isEqualTo("media-1")
    }

    @Test
    fun search_byMediaUploader_matchesMedia() {
        val media = MediaEntity.fromDomain(
            MediaItem(
                id = "media-2",
                sourceUrl = "https://youtube.com/watch?v=123",
                title = "Zoo Clip",
                uploader = "jawed",
                createdAt = 1000L
            )
        )
        val state = LibraryViewModel.buildUiState(
            callsEntities = emptyList(),
            mediaEntities = listOf(media),
            filter = LibraryFilter.ALL,
            rawQuery = "jawed",
            evaluator = evaluator
        )
        assertThat(state.items).hasSize(1)
        assertThat(state.items.first().id).isEqualTo("media-2")
    }

    @Test
    fun search_bySourcePlatform_matchesMedia() {
        val youtubeMedia = MediaEntity.fromDomain(
            MediaItem(
                id = "media-yt",
                sourceUrl = "https://youtube.com/watch?v=123",
                title = "Video One",
                sourcePlatform = MediaSource.YOUTUBE,
                createdAt = 1000L
            )
        )
        val genericMedia = MediaEntity.fromDomain(
            MediaItem(
                id = "media-gen",
                sourceUrl = "https://example.com/video.mp4",
                title = "Video Two",
                sourcePlatform = MediaSource.GENERIC_URL,
                createdAt = 2000L
            )
        )
        val state = LibraryViewModel.buildUiState(
            callsEntities = emptyList(),
            mediaEntities = listOf(youtubeMedia, genericMedia),
            filter = LibraryFilter.ALL,
            rawQuery = "YOUTUBE",
            evaluator = evaluator
        )
        assertThat(state.items).hasSize(1)
        assertThat(state.items.first().id).isEqualTo("media-yt")
    }

    @Test
    fun search_byResolution_matchesMedia() {
        val media = MediaEntity.fromDomain(
            MediaItem(
                id = "media-res",
                sourceUrl = "https://example.com/video.mp4",
                title = "HD Stream",
                resolution = "1080p",
                createdAt = 1000L
            )
        )
        val state = LibraryViewModel.buildUiState(
            callsEntities = emptyList(),
            mediaEntities = listOf(media),
            filter = LibraryFilter.ALL,
            rawQuery = "1080p",
            evaluator = evaluator
        )
        assertThat(state.items).hasSize(1)
        assertThat(state.items.first().id).isEqualTo("media-res")
    }

    @Test
    fun search_byFormatSelected_matchesMedia() {
        val media = MediaEntity.fromDomain(
            MediaItem(
                id = "media-fmt",
                sourceUrl = "https://youtube.com/watch?v=123",
                title = "Audio Stream",
                formatSelected = "youtube:audio:itag:139",
                createdAt = 1000L
            )
        )
        val state = LibraryViewModel.buildUiState(
            callsEntities = emptyList(),
            mediaEntities = listOf(media),
            filter = LibraryFilter.ALL,
            rawQuery = "itag:139",
            evaluator = evaluator
        )
        assertThat(state.items).hasSize(1)
        assertThat(state.items.first().id).isEqualTo("media-fmt")
    }

    @Test
    fun search_withWhitespaceAndCase_isTrimmedAndCaseInsensitive() {
        val call = CallEntity.fromDomain(
            CallSession(
                id = "call-trim",
                phoneNumber = "+12345",
                contactName = "Bob Builder",
                direction = CallDirection.INCOMING,
                startTimeEpochMs = 1000L,
                createdAt = 1000L
            )
        )
        val state = LibraryViewModel.buildUiState(
            callsEntities = listOf(call),
            mediaEntities = emptyList(),
            filter = LibraryFilter.ALL,
            rawQuery = "   bOb   ",
            evaluator = evaluator
        )
        assertThat(state.items).hasSize(1)
        assertThat(state.items.first().id).isEqualTo("call-trim")
    }

    @Test
    fun search_blankQuery_returnsAllItems() {
        val call = CallEntity.fromDomain(
            CallSession(
                id = "call-all",
                phoneNumber = "+12345",
                direction = CallDirection.INCOMING,
                startTimeEpochMs = 1000L,
                createdAt = 1000L
            )
        )
        val media = MediaEntity.fromDomain(
            MediaItem(
                id = "media-all",
                sourceUrl = "https://example.com/video.mp4",
                title = "Test",
                createdAt = 2000L
            )
        )
        val state = LibraryViewModel.buildUiState(
            callsEntities = listOf(call),
            mediaEntities = listOf(media),
            filter = LibraryFilter.ALL,
            rawQuery = "   ",
            evaluator = evaluator
        )
        assertThat(state.items).hasSize(2)
    }

    // ==========================================
    // FILTER AND SORTING TESTS
    // ==========================================

    @Test
    fun filters_correctlySegregateCategories() {
        val call = CallEntity.fromDomain(
            CallSession(
                id = "call-item",
                phoneNumber = "+111",
                direction = CallDirection.INCOMING,
                startTimeEpochMs = 1000L,
                createdAt = 1000L,
                hasTranscript = true
            )
        )
        val mediaWithTranscript = MediaEntity.fromDomain(
            MediaItem(
                id = "media-with-t",
                sourceUrl = "https://example.com/v1",
                title = "Media With Transcript",
                createdAt = 2000L,
                hasTranscript = true
            )
        )
        val mediaWithoutTranscript = MediaEntity.fromDomain(
            MediaItem(
                id = "media-no-t",
                sourceUrl = "https://example.com/v2",
                title = "Media No Transcript",
                createdAt = 3000L,
                hasTranscript = false
            )
        )
        val calls = listOf(call)
        val media = listOf(mediaWithTranscript, mediaWithoutTranscript)

        // ALL
        val allState = LibraryViewModel.buildUiState(calls, media, LibraryFilter.ALL, "", evaluator)
        assertThat(allState.items).hasSize(3)

        // CALLS
        val callsState = LibraryViewModel.buildUiState(calls, media, LibraryFilter.CALLS, "", evaluator)
        assertThat(callsState.items).hasSize(1)
        assertThat(callsState.items.first().id).isEqualTo("call-item")

        // MEDIA
        val mediaState = LibraryViewModel.buildUiState(calls, media, LibraryFilter.MEDIA, "", evaluator)
        assertThat(mediaState.items).hasSize(2)

        // TRANSCRIPTS (Items with hasTranscript == true)
        val transcriptState = LibraryViewModel.buildUiState(calls, media, LibraryFilter.TRANSCRIPTS, "", evaluator)
        assertThat(transcriptState.items).hasSize(2)
        assertThat(transcriptState.items.map { it.id }).containsExactly("media-with-t", "call-item")
    }

    @Test
    fun sort_isDeterministicCreatedAtDescending() {
        val item1 = CallEntity.fromDomain(
            CallSession(id = "c1", phoneNumber = "1", direction = CallDirection.INCOMING, startTimeEpochMs = 100L, createdAt = 100L)
        )
        val item2 = MediaEntity.fromDomain(
            MediaItem(id = "m2", sourceUrl = "url", title = "T2", createdAt = 500L)
        )
        val item3 = CallEntity.fromDomain(
            CallSession(id = "c3", phoneNumber = "3", direction = CallDirection.INCOMING, startTimeEpochMs = 300L, createdAt = 300L)
        )
        val state = LibraryViewModel.buildUiState(
            callsEntities = listOf(item1, item3),
            mediaEntities = listOf(item2),
            filter = LibraryFilter.ALL,
            rawQuery = "",
            evaluator = evaluator
        )
        assertThat(state.items.map { it.id }).containsExactly("m2", "c3", "c1").inOrder()
    }

    // ==========================================
    // METRICS TRUTH TESTS
    // ==========================================

    @Test
    fun metrics_reflectActualAvailableFilesAndBytes() {
        val availableCall = CallEntity.fromDomain(
            CallSession(
                id = "call-avail",
                phoneNumber = "1",
                direction = CallDirection.INCOMING,
                startTimeEpochMs = 100L,
                audioFilePath = "/path/to/available.m4a",
                fileSizeBytes = 1000L,
                hasTranscript = true,
                createdAt = 100L
            )
        )
        val missingCall = CallEntity.fromDomain(
            CallSession(
                id = "call-missing",
                phoneNumber = "2",
                direction = CallDirection.INCOMING,
                startTimeEpochMs = 200L,
                audioFilePath = "missing_path.m4a",
                fileSizeBytes = 5000L,
                hasTranscript = false,
                createdAt = 200L
            )
        )
        val availableMedia = MediaEntity.fromDomain(
            MediaItem(
                id = "media-avail",
                sourceUrl = "url",
                title = "Media Avail",
                localFilePath = "/path/to/available.mp4",
                fileSizeBytes = 4000L,
                downloadStatus = DownloadStatus.COMPLETED,
                hasTranscript = true,
                createdAt = 300L
            )
        )

        val state = LibraryViewModel.buildUiState(
            callsEntities = listOf(availableCall, missingCall),
            mediaEntities = listOf(availableMedia),
            filter = LibraryFilter.ALL,
            rawQuery = "",
            evaluator = evaluator
        )

        assertThat(state.indexedItemCount).isEqualTo(3)
        assertThat(state.totalCallCount).isEqualTo(2)
        assertThat(state.totalMediaCount).isEqualTo(1)
        assertThat(state.totalTranscriptsCount).isEqualTo(2)
        assertThat(state.availableFileCount).isEqualTo(2)
        assertThat(state.unavailableFileCount).isEqualTo(1)
        assertThat(state.availableLocalBytes).isEqualTo(5000L)
        assertThat(state.totalVaultSizeBytes).isEqualTo(5000L)
    }

    // ==========================================
    // REACTIVE STATEFLOW INTEGRATION TEST
    // ==========================================

    @Test
    fun reactiveFlow_updatesUiStateWhenDaoFlowsEmit() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val viewModel = createViewModel(scope)

        assertThat(viewModel.uiState.value.items).isEmpty()

        val call = CallEntity.fromDomain(
            CallSession(
                id = "call-reactive",
                phoneNumber = "+12345",
                contactName = "Test Contact",
                direction = CallDirection.INCOMING,
                startTimeEpochMs = 1000L,
                createdAt = 1000L
            )
        )
        fakeCallDao.callsFlow.value = listOf(call)

        assertThat(viewModel.uiState.value.items).hasSize(1)
        assertThat(viewModel.uiState.value.items.first().id).isEqualTo("call-reactive")
    }
}
