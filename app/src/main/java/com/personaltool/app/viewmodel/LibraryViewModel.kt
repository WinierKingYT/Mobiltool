package com.personaltool.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personaltool.core.storage.dao.CallDao
import com.personaltool.core.storage.dao.MediaDao
import com.personaltool.core.storage.entity.CallEntity
import com.personaltool.core.storage.entity.MediaEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

enum class LibraryFilter {
    ALL,
    CALLS,
    MEDIA,
    TRANSCRIPTS
}

data class LibraryUiState(
    val items: List<VaultItem> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val searchQuery: String = "",
    val totalCallCount: Int = 0,
    val totalMediaCount: Int = 0,
    val totalTranscriptsCount: Int = 0,
    val indexedItemCount: Int = 0,
    val availableFileCount: Int = 0,
    val unavailableFileCount: Int = 0,
    val availableLocalBytes: Long = 0L,
    val totalVaultSizeBytes: Long = 0L
)

class LibraryViewModel(
    private val callDao: CallDao,
    private val mediaDao: MediaDao,
    private val evaluator: VaultItemEvaluator = DefaultVaultItemEvaluator(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope
    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    private val _searchQuery = MutableStateFlow("")

    // Stage 1: Filesystem evaluation runs ONLY when DAO emissions change, isolated on ioDispatcher
    private val evaluatedVaultSnapshot: Flow<List<VaultItem>> = combine(
        callDao.getAllCallsFlow(),
        mediaDao.getAllMediaFlow()
    ) { callsEntities, mediaEntities ->
        evaluateVaultSnapshot(callsEntities, mediaEntities, evaluator)
    }.flowOn(ioDispatcher)

    // Stage 2: Pure in-memory filter and search transformations, idle-safe WhileSubscribed lifecycle
    val uiState: StateFlow<LibraryUiState> = combine(
        evaluatedVaultSnapshot,
        _filter,
        _searchQuery
    ) { snapshot, filter: LibraryFilter, rawQuery: String ->
        filterAndSearchVaultSnapshot(snapshot, filter, rawQuery)
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    fun setFilter(filter: LibraryFilter) {
        _filter.value = filter
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    companion object {
        fun evaluateVaultSnapshot(
            callsEntities: List<CallEntity>,
            mediaEntities: List<MediaEntity>,
            evaluator: VaultItemEvaluator = DefaultVaultItemEvaluator()
        ): List<VaultItem> {
            val calls = callsEntities.map { it.toDomain() }
            val media = mediaEntities.map { it.toDomain() }

            val allVaultItems = ArrayList<VaultItem>(calls.size + media.size)
            calls.forEach { allVaultItems.add(evaluator.evaluateCall(it)) }
            media.forEach { allVaultItems.add(evaluator.evaluateMedia(it)) }

            return allVaultItems.sortedByDescending { it.createdAt }
        }

        fun filterAndSearchVaultSnapshot(
            snapshot: List<VaultItem>,
            filter: LibraryFilter,
            rawQuery: String
        ): LibraryUiState {
            val query = rawQuery.trim()

            val filteredItems = snapshot.filter { item ->
                val matchesFilter = when (filter) {
                    LibraryFilter.ALL -> true
                    LibraryFilter.CALLS -> item is VaultItem.Call
                    LibraryFilter.MEDIA -> item is VaultItem.Media
                    LibraryFilter.TRANSCRIPTS -> item.hasTranscript
                }
                val matchesQuery = if (query.isEmpty()) {
                    true
                } else {
                    when (item) {
                        is VaultItem.Call -> {
                            val session = item.session
                            (session.contactName?.contains(query, ignoreCase = true) == true) ||
                                    session.phoneNumber.contains(query, ignoreCase = true) ||
                                    session.direction.name.contains(query, ignoreCase = true)
                        }
                        is VaultItem.Media -> {
                            val mediaItem = item.item
                            mediaItem.title.contains(query, ignoreCase = true) ||
                                    (mediaItem.uploader?.contains(query, ignoreCase = true) == true) ||
                                    mediaItem.sourcePlatform.name.contains(query, ignoreCase = true) ||
                                    (mediaItem.resolution?.contains(query, ignoreCase = true) == true) ||
                                    (mediaItem.formatSelected?.contains(query, ignoreCase = true) == true)
                        }
                    }
                }
                matchesFilter && matchesQuery
            }

            val availableFiles = snapshot.count { it.fileState == VaultFileState.AVAILABLE }
            val unavailableFiles = snapshot.size - availableFiles
            val availableBytes = snapshot.sumOf { it.availableSizeBytes }
            val transcriptCount = snapshot.count { it.hasTranscript }
            val callCount = snapshot.count { it is VaultItem.Call }
            val mediaCount = snapshot.count { it is VaultItem.Media }

            return LibraryUiState(
                items = filteredItems,
                filter = filter,
                searchQuery = rawQuery,
                totalCallCount = callCount,
                totalMediaCount = mediaCount,
                totalTranscriptsCount = transcriptCount,
                indexedItemCount = snapshot.size,
                availableFileCount = availableFiles,
                unavailableFileCount = unavailableFiles,
                availableLocalBytes = availableBytes,
                totalVaultSizeBytes = availableBytes
            )
        }
    }
}
