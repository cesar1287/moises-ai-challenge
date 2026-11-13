package com.example.moisessongs.ui.songs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moisessongs.domain.SearchPolicy
import com.example.moisessongs.domain.SearchSnapshot
import com.example.moisessongs.domain.Song
import com.example.moisessongs.domain.SongRepository
import com.example.moisessongs.ui.common.SavedStateKeys
import com.example.moisessongs.ui.common.UiFailure
import com.example.moisessongs.ui.common.toUiFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SongsUiState(
    val input: String = "",
    val query: String = "",
    val recentSongs: List<Song> = emptyList(),
    val snapshot: SearchSnapshot? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val failure: UiFailure? = null,
    val appendFailed: Boolean = false,
) {
    val songs: List<Song> get() = if (query.isBlank()) recentSongs else snapshot?.songs.orEmpty()
}

class SongsViewModel(
    private val repository: SongRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SongsUiState(input = savedState[SavedStateKeys.SEARCH_INPUT] ?: ""))
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    private var request: Job? = null
    private var recentObservation: Job? = null
    private var recentFailure: UiFailure? = null

    init {
        observeRecentSongs()
        val restored = savedState.get<String>(SavedStateKeys.SEARCH_QUERY).orEmpty()
        if (restored.isNotBlank()) {
            val editedInput = state.value.input
            search(restored)
            editInput(editedInput)
        }
    }

    private fun observeRecentSongs() {
        recentObservation?.cancel()
        recentFailure = null
        if (state.value.query.isBlank()) mutableState.update { it.copy(failure = null) }
        recentObservation = viewModelScope.launch {
            repository.observeRecentSongs().catch { failure ->
                recentFailure = failure.toUiFailure()
                if (state.value.query.isBlank()) mutableState.update { it.copy(failure = recentFailure) }
            }.collect { songs ->
                recentFailure = null
                mutableState.update { it.copy(recentSongs = songs,
                    failure = if (it.query.isBlank()) null else it.failure) }
            }
        }
    }

    fun editInput(value: String) {
        savedState[SavedStateKeys.SEARCH_INPUT] = value
        mutableState.update { it.copy(input = value) }
        if (value.isBlank() && state.value.query.isNotBlank()) clearSearch()
    }

    fun submit() = search(state.value.input)

    fun search(value: String) {
        val query = SearchPolicy.normalize(value)
        if (query.isBlank()) { clearSearch(); return }
        observation?.cancel()
        request?.cancel()
        savedState[SavedStateKeys.SEARCH_QUERY] = query
        savedState[SavedStateKeys.SEARCH_INPUT] = value
        mutableState.update { it.copy(input = value, query = query, snapshot = null,
            loading = true, loadingMore = false, failure = null, appendFailed = false) }
        observation = viewModelScope.launch {
            repository.observeSearch(query).catch {
                mutableState.update { it.copy(failure = UiFailure.UNEXPECTED) }
            }.collect { snapshot ->
                mutableState.update { it.copy(snapshot = snapshot) }
            }
        }
        load(refresh = true)
    }

    fun clearSearch() {
        observation?.cancel()
        request?.cancel()
        savedState[SavedStateKeys.SEARCH_QUERY] = ""
        savedState[SavedStateKeys.SEARCH_INPUT] = ""
        mutableState.update { it.copy(input = "", query = "", snapshot = null,
            loading = false, loadingMore = false, failure = recentFailure, appendFailed = false) }
    }

    fun refresh() {
        if (state.value.query.isBlank()) observeRecentSongs()
        else if (request?.isActive != true) load(refresh = true)
    }

    fun loadMore() {
        if (state.value.query.isBlank() || request?.isActive == true ||
            state.value.snapshot == null || state.value.snapshot?.endReached == true) return
        load(refresh = false)
    }

    private fun load(refresh: Boolean) {
        val query = state.value.query
        mutableState.update { it.copy(loading = refresh, loadingMore = !refresh, failure = null, appendFailed = false) }
        request = viewModelScope.launch {
            try {
                repository.loadSearch(query, refresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(failure = failure.toUiFailure(), appendFailed = !refresh) }
            } finally {
                if (currentCoroutineContext().isActive && state.value.query == query) {
                    mutableState.update { it.copy(loading = false, loadingMore = false) }
                }
            }
        }
    }
}
