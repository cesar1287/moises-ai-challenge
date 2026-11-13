package com.example.moisessongs.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moisessongs.domain.Album
import com.example.moisessongs.domain.SongRepository
import com.example.moisessongs.ui.common.UiFailure
import com.example.moisessongs.ui.common.toUiFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumUiState(val album: Album? = null, val loading: Boolean = true, val failure: UiFailure? = null)

class AlbumViewModel(private val id: Long, private val repository: SongRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AlbumUiState())
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    private fun observeAlbum() {
        observation = viewModelScope.launch {
            repository.observeAlbum(id).catch { failure ->
                mutableState.update { it.copy(failure = failure.toUiFailure(), loading = false) }
            }.collect { album -> mutableState.update { it.copy(album = album) } }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        mutableState.update { it.copy(loading = true, failure = null) }
        if (observation?.isActive != true) observeAlbum()
        refreshJob = viewModelScope.launch {
            try {
                repository.refreshAlbum(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(failure = failure.toUiFailure()) }
            } finally {
                mutableState.update { it.copy(loading = false) }
            }
        }
    }
}
