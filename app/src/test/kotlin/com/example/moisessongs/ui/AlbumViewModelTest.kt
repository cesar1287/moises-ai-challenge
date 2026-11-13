package com.example.moisessongs.ui

import androidx.lifecycle.ViewModelStore
import com.example.moisessongs.*
import com.example.moisessongs.data.DefaultSongRepository
import com.example.moisessongs.domain.DataFailure
import com.example.moisessongs.domain.DataFailureException
import com.example.moisessongs.domain.SongRepository
import com.example.moisessongs.ui.album.AlbumViewModel
import com.example.moisessongs.ui.common.UiFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun `refresh reconnects a failed album observer without duplicating a healthy subscription`() = runTest {
        val store = MemorySongStore()
        val catalog = FakeCatalog()
        val repository = DefaultSongRepository(catalog, store, computationDispatcher = main.dispatcher)
        var readFailed = true
        var subscriptions = 0
        val source = object : SongRepository by repository {
            override fun observeAlbum(id: Long) = flow {
                subscriptions++
                if (readFailed) throw DataFailureException(DataFailure.STORAGE, IOException("temporary read failure"))
                emitAll(repository.observeAlbum(id))
            }
        }
        val model = AlbumViewModel(10, source)
        val models = ViewModelStore().also { it.put("album", model) }
        try {
            runCurrent()
            assertEquals(UiFailure.UNEXPECTED, model.state.value.failure)
            assertNull(model.state.value.album)
            model.refresh()
            runCurrent()
            assertEquals(UiFailure.UNEXPECTED, model.state.value.failure)
            assertFalse(model.state.value.loading)

            readFailed = false
            model.refresh()
            runCurrent()
            assertEquals(album(), model.state.value.album)
            assertNull(model.state.value.failure)

            catalog.lookup = { album(listOf(song(2))) }
            model.refresh()
            runCurrent()
            assertEquals(listOf(song(2)), model.state.value.album!!.songs)
            assertEquals(3, subscriptions)
        } finally { models.clear() }
    }

    @Test fun `cached album survives offline refresh and retry clears the failure`() = runTest {
        val catalog = FakeCatalog()
        val store = MemorySongStore()
        store.saveAlbum(album())
        catalog.lookup = { throw IOException("offline") }
        val model = AlbumViewModel(10, DefaultSongRepository(catalog, store, computationDispatcher = main.dispatcher))
        val models = ViewModelStore().also { it.put("album", model) }
        try {
            runCurrent()
            assertEquals(album(), model.state.value.album)
            assertFalse(model.state.value.loading)
            assertEquals(UiFailure.CONNECTION, model.state.value.failure)
            catalog.lookup = { album(listOf(song(2))) }
            model.refresh()
            runCurrent()
            assertNull(model.state.value.failure)
            assertEquals(listOf(song(2)), model.state.value.album!!.songs)
        } finally { models.clear() }
    }
}
