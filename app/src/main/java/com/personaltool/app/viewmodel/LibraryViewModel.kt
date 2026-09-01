package com.personaltool.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.media.MediaItem
import com.personaltool.core.storage.dao.CallDao
import com.personaltool.core.storage.dao.MediaDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class LibraryFilter {
    ALL,
    CALLS,
    MEDIA,
    TRANSCRIPTS
}

sealed interface VaultItem {
    val id: String
    val title: String
    val createdAt: Long
    val hasTranscript: Boolean

    data class Call(val session: CallSession) : VaultItem {
        override val id: String = session.id
        override val title: String = session.contactName ?: session.phoneNumber
        override val createdAt: Long = session.createdAt
        override val hasTranscript: Boolean = session.hasTranscript
    }

    data class Media(val item: MediaItem) : VaultItem {
        override val id: String = item.id
        override val title: String = item.title
        override val createdAt: Long = item.createdAt
        override val hasTranscript: Boolean = item.hasTranscript
    }
}

data class LibraryUiState(
    val items: List<VaultItem> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val searchQuery: String = "",
    val totalCallCount: Int = 0,
    val totalMediaCount: Int = 0,
    val totalTranscriptsCount: Int = 0,
    val totalVaultSizeBytes: Long = 0L
)

class LibraryViewModel(
    private val callDao: CallDao,
    private val mediaDao: MediaDao
) : ViewModel() {

    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<LibraryUiState> = combine(
        callDao.getAllCallsFlow(),
        mediaDao.getAllMediaFlow(),
        _filter,
        _searchQuery
    ) { callsEntities, mediaEntities, filter: LibraryFilter, query: String ->
        val calls = callsEntities.map { it.toDomain() }
        val media = mediaEntities.map { it.toDomain() }

        val allVaultItems = mutableListOf<VaultItem>()
        calls.forEach { allVaultItems.add(VaultItem.Call(it)) }
        media.forEach { allVaultItems.add(VaultItem.Media(it)) }

        val sortedItems = allVaultItems.sortedByDescending { it.createdAt }

        val filteredItems = sortedItems.filter { item ->
            val matchesFilter = when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.CALLS -> item is VaultItem.Call
                LibraryFilter.MEDIA -> item is VaultItem.Media
                LibraryFilter.TRANSCRIPTS -> item.hasTranscript
            }
            val matchesQuery = query.isBlank() || item.title.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }

        val totalSize = calls.sumOf { it.fileSizeBytes } + media.sumOf { it.fileSizeBytes }
        val transcriptCount = calls.count { it.hasTranscript } + media.count { it.hasTranscript }

        LibraryUiState(
            items = filteredItems,
            filter = filter,
            searchQuery = query,
            totalCallCount = calls.size,
            totalMediaCount = media.size,
            totalTranscriptsCount = transcriptCount,
            totalVaultSizeBytes = totalSize
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    fun setFilter(filter: LibraryFilter) {
        _filter.value = filter
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
