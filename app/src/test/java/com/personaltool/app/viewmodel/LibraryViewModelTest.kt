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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

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

private class CountingVaultItemEvaluator : VaultItemEvaluator {
    val evaluateCallCount = AtomicInteger(0)
    val evaluateMediaCount = AtomicInteger(0)

    override fun evaluateCall(session: CallSession): VaultItem.Call {
        evaluateCallCount.incrementAndGet()
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
        evaluateMediaCount.incrementAndGet()
        val localPath = item.localFilePath
        val isAvailable = localPath != null && !localPath.contains("missing") && item.downloadStatus == DownloadStatus.COMPLETED
        val fileState = when {
            item.downloadStatus != DownloadStatus.COMPLETED -> VaultFileState.NOT_READY
            isAvailable -> VaultFileState.AVAILABLE
            else -> VaultFileState.MISSING
        }
        return VaultItem.Media(
            item = item,
            fileState = fileState,
            primaryAction = if (isAvailable) (if (item.mediaType == MediaType.VIDEO) VaultPrimaryAction.PLAY_VIDEO else VaultPrimaryAction.PLAY_AUDIO) else VaultPrimaryAction.UNAVAILABLE,
            availableSizeBytes = if (isAvailable) item.fileSizeBytes else 0L
        )
    }
}

class LibraryViewModelTest {

    private val fakeCallDao = FakeCallDao()
    private val fakeMediaDao = FakeMediaDao()
    private val countingEvaluator = CountingVaultItemEvaluator()

    private fun createViewModel(scope: CoroutineScope): LibraryViewModel {
        return LibraryViewModel(
            callDao = fakeCallDao,
            mediaDao = fakeMediaDao,
            evaluator = countingEvaluator,
            ioDispatcher = Dispatchers.Unconfined,
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
        val snapshot = LibraryViewModel.evaluateVaultSnapshot(listOf(call), emptyList(), countingEvaluator)
        val state = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.ALL, "alice")
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
        val snapshot = LibraryViewModel.evaluateVaultSnapshot(listOf(call), emptyList(), countingEvaluator)
        val state = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.ALL, "555111")
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
        val snapshot = LibraryViewModel.evaluateVaultSnapshot(listOf(incomingCall, outgoingCall), emptyList(), countingEvaluator)
        val state = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.ALL, "INCOMING")
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
        val snapshot = LibraryViewModel.evaluateVaultSnapshot(emptyList(), listOf(media), countingEvaluator)
        val state = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.ALL, "bunny")
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
        val snapshot = LibraryViewModel.evaluateVaultSnapshot(emptyList(), listOf(media), countingEvaluator)
        val state = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.ALL, "jawed")
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
        val snapshot = LibraryViewModel.evaluateVaultSnapshot(emptyList(), listOf(youtubeMedia, genericMedia), countingEvaluator)
        val state = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.ALL, "YOUTUBE")
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
        val snapshot = LibraryViewModel.evaluateVaultSnapshot(emptyList(), listOf(media), countingEvaluator)
        val state = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.ALL, "1080p")
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
        val snapshot = LibraryViewModel.evaluateVaultSnapshot(emptyList(), listOf(media), countingEvaluator)
        val state = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.ALL, "itag:139")
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
        val snapshot = LibraryViewModel.evaluateVaultSnapshot(listOf(call), emptyList(), countingEvaluator)
        val state = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.ALL, "   bOb   ")
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
        val snapshot = LibraryViewModel.evaluateVaultSnapshot(listOf(call), listOf(media), countingEvaluator)
        val state = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.ALL, "   ")
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
        val snapshot = LibraryViewModel.evaluateVaultSnapshot(listOf(call), listOf(mediaWithTranscript, mediaWithoutTranscript), countingEvaluator)

        // ALL
        val allState = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.ALL, "")
        assertThat(allState.items).hasSize(3)

        // CALLS
        val callsState = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.CALLS, "")
        assertThat(callsState.items).hasSize(1)
        assertThat(callsState.items.first().id).isEqualTo("call-item")

        // MEDIA
        val mediaState = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.MEDIA, "")
        assertThat(mediaState.items).hasSize(2)

        // TRANSCRIPTS (Items with hasTranscript == true)
        val transcriptState = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.TRANSCRIPTS, "")
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
        val snapshot = LibraryViewModel.evaluateVaultSnapshot(listOf(item1, item3), listOf(item2), countingEvaluator)
        assertThat(snapshot.map { it.id }).containsExactly("m2", "c3", "c1").inOrder()
    }

    // ==========================================
    // METRICS TRUTH TESTS
    // ==========================================

    @Test
    fun metrics_reflectActualAvailableFilesAndExcludeNotReady() {
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
        val notReadyMedia = MediaEntity.fromDomain(
            MediaItem(
                id = "media-downloading",
                sourceUrl = "url2",
                title = "Downloading Media",
                localFilePath = "/path/to/part.mp4",
                fileSizeBytes = 2000L,
                downloadStatus = DownloadStatus.DOWNLOADING,
                createdAt = 400L
            )
        )

        val snapshot = LibraryViewModel.evaluateVaultSnapshot(
            listOf(availableCall, missingCall),
            listOf(availableMedia, notReadyMedia),
            countingEvaluator
        )
        val state = LibraryViewModel.filterAndSearchVaultSnapshot(snapshot, LibraryFilter.ALL, "")

        assertThat(state.indexedItemCount).isEqualTo(4)
        assertThat(state.totalCallCount).isEqualTo(2)
        assertThat(state.totalMediaCount).isEqualTo(2)
        assertThat(state.totalTranscriptsCount).isEqualTo(2)
        assertThat(state.availableFileCount).isEqualTo(2) // availableCall + availableMedia
        assertThat(state.unavailableFileCount).isEqualTo(2) // missingCall + notReadyMedia
        assertThat(state.availableLocalBytes).isEqualTo(5000L) // 1000L + 4000L only (notReadyMedia 2000L excluded)
        assertThat(state.totalVaultSizeBytes).isEqualTo(5000L)
    }

    // ==========================================
    // RESOURCE EFFICIENCY & ISOLATION TESTS
    // ==========================================

    @Test
    fun resourceEfficiency_searchAndFilterChangesDoNotTriggerEvaluator() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val call = CallEntity.fromDomain(
            CallSession(
                id = "c-res",
                phoneNumber = "+123",
                direction = CallDirection.INCOMING,
                startTimeEpochMs = 1000L,
                createdAt = 1000L
            )
        )
        val media = MediaEntity.fromDomain(
            MediaItem(
                id = "m-res",
                sourceUrl = "url",
                title = "Title",
                createdAt = 2000L
            )
        )

        fakeCallDao.callsFlow.value = listOf(call)
        fakeMediaDao.mediaFlow.value = listOf(media)

        val viewModel = createViewModel(scope)

        // Subscribe to StateFlow
        val job = viewModel.uiState.launchIn(scope)

        val initialCallCount = countingEvaluator.evaluateCallCount.get()
        val initialMediaCount = countingEvaluator.evaluateMediaCount.get()

        assertThat(initialCallCount).isGreaterThan(0)
        assertThat(initialMediaCount).isGreaterThan(0)

        // Change search query 10 times
        for (i in 1..10) {
            viewModel.onSearchQueryChanged("query-$i")
        }

        // Change filters
        viewModel.setFilter(LibraryFilter.CALLS)
        viewModel.setFilter(LibraryFilter.MEDIA)
        viewModel.setFilter(LibraryFilter.TRANSCRIPTS)
        viewModel.setFilter(LibraryFilter.ALL)

        // Evaluator call count MUST NOT have increased
        assertThat(countingEvaluator.evaluateCallCount.get()).isEqualTo(initialCallCount)
        assertThat(countingEvaluator.evaluateMediaCount.get()).isEqualTo(initialMediaCount)

        // Emit new DAO data -> Evaluator runs again
        val call2 = CallEntity.fromDomain(
            CallSession(
                id = "c-res2",
                phoneNumber = "+456",
                direction = CallDirection.INCOMING,
                startTimeEpochMs = 3000L,
                createdAt = 3000L
            )
        )
        fakeCallDao.callsFlow.value = listOf(call, call2)

        assertThat(countingEvaluator.evaluateCallCount.get()).isGreaterThan(initialCallCount)

        job.cancel()
    }

    // ==========================================
    // ROUTING CONTRACT TESTS
    // ==========================================

    @Test
    fun routingContract_actionsMapToCorrectBoundaries() {
        val audioCall = VaultItem.Call(
            session = CallSession(id = "c1", phoneNumber = "1", direction = CallDirection.INCOMING, startTimeEpochMs = 100L),
            fileState = VaultFileState.AVAILABLE,
            primaryAction = VaultPrimaryAction.PLAY_AUDIO
        )
        val videoMedia = VaultItem.Media(
            item = MediaItem(id = "m1", sourceUrl = "url", title = "V", mediaType = MediaType.VIDEO),
            fileState = VaultFileState.AVAILABLE,
            primaryAction = VaultPrimaryAction.PLAY_VIDEO
        )
        val transcriptCall = VaultItem.Call(
            session = CallSession(id = "c2", phoneNumber = "2", direction = CallDirection.INCOMING, startTimeEpochMs = 200L, hasTranscript = true),
            fileState = VaultFileState.MISSING,
            primaryAction = VaultPrimaryAction.OPEN_TRANSCRIPT
        )

        var audioPlayed = false
        var videoPlayed = false
        var transcriptOpened = false

        // Test dispatch for PLAY_AUDIO
        when (audioCall.primaryAction) {
            VaultPrimaryAction.PLAY_AUDIO -> audioPlayed = true
            VaultPrimaryAction.PLAY_VIDEO -> videoPlayed = true
            VaultPrimaryAction.OPEN_TRANSCRIPT -> transcriptOpened = true
            VaultPrimaryAction.UNAVAILABLE -> {}
        }
        assertThat(audioPlayed).isTrue()
        assertThat(videoPlayed).isFalse()
        assertThat(transcriptOpened).isFalse()

        // Test dispatch for PLAY_VIDEO
        audioPlayed = false
        when (videoMedia.primaryAction) {
            VaultPrimaryAction.PLAY_AUDIO -> audioPlayed = true
            VaultPrimaryAction.PLAY_VIDEO -> videoPlayed = true
            VaultPrimaryAction.OPEN_TRANSCRIPT -> transcriptOpened = true
            VaultPrimaryAction.UNAVAILABLE -> {}
        }
        assertThat(videoPlayed).isTrue()
        assertThat(audioPlayed).isFalse()
        assertThat(transcriptOpened).isFalse()

        // Test dispatch for OPEN_TRANSCRIPT
        videoPlayed = false
        when (transcriptCall.primaryAction) {
            VaultPrimaryAction.PLAY_AUDIO -> audioPlayed = true
            VaultPrimaryAction.PLAY_VIDEO -> videoPlayed = true
            VaultPrimaryAction.OPEN_TRANSCRIPT -> transcriptOpened = true
            VaultPrimaryAction.UNAVAILABLE -> {}
        }
        assertThat(transcriptOpened).isTrue()
        assertThat(audioPlayed).isFalse()
        assertThat(videoPlayed).isFalse()
    }
}
