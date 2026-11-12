package com.example.moisessongs.ui

import androidx.lifecycle.ViewModelStore
import com.example.moisessongs.*
import com.example.moisessongs.data.DefaultSongRepository
import com.example.moisessongs.ui.album.AlbumViewModel
import com.example.moisessongs.ui.common.UiFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModelTest {
    @get:Rule val main = MainDispatcherRule()

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
