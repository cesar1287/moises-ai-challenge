package com.example.moisessongs.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.moisessongs.*
import com.example.moisessongs.data.DefaultSongRepository
import com.example.moisessongs.domain.CatalogSearch
import com.example.moisessongs.domain.DataFailure
import com.example.moisessongs.domain.DataFailureException
import com.example.moisessongs.domain.SearchSnapshot
import com.example.moisessongs.domain.SongRepository
import com.example.moisessongs.ui.common.SavedStateKeys
import com.example.moisessongs.ui.common.UiFailure
import com.example.moisessongs.ui.songs.SongsViewModel
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SongsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val catalog = FakeCatalog()
    private val store = MemorySongStore()
    private val repository = DefaultSongRepository(catalog, store, computationDispatcher = main.dispatcher)
    private val models = ViewModelStore()
    private fun model(saved: SavedStateHandle = SavedStateHandle()) = SongsViewModel(repository, saved)
        .also { models.put("songs", it) }
    @After fun cleanup() = models.clear()

    @Test fun `cached songs display while refresh is pending and remain after it fails`() = runTest {
        store.saveSearch("artist", SearchSnapshot(listOf(song()), 25))
        val gate = CompletableDeferred<Unit>()
        catalog.search = { _, _ -> gate.await(); throw IOException("offline") }
        val vm = model()
        vm.search("Artist")
        runCurrent()
        assertEquals(listOf(song()), vm.state.value.songs)
        assertTrue(vm.state.value.loading)
        gate.complete(Unit)
        runCurrent()
        assertFalse(vm.state.value.loading)
        assertEquals(UiFailure.CONNECTION, vm.state.value.failure)
        assertEquals(listOf(song()), vm.state.value.songs)
    }

    @Test fun `new search cancels old request and ignores late cache emissions`() = runTest {
        var oldCancelled = false
        catalog.search = { query, _ ->
            if (query == "old") try { awaitCancellation() } finally { oldCancelled = true }
            CatalogSearch(listOf(song(2)), 1)
        }
        val vm = model()
        vm.search("old")
        runCurrent()
        vm.search("new")
        runCurrent()
        store.saveSearch("old", SearchSnapshot(listOf(song(99)), 25))
        runCurrent()
        assertTrue(oldCancelled)
        assertEquals("new", vm.state.value.query)
        assertEquals(listOf(2L), vm.state.value.songs.map { it.id })
        assertNull(vm.state.value.failure)
    }

    @Test fun `cancelled repeat of the same query cannot clear the new loading state`() = runTest {
        catalog.search = { _, _ -> awaitCancellation() }
        val vm = model()
        vm.search("same")
        runCurrent()
        vm.search("same")
        runCurrent()
        assertTrue(vm.state.value.loading)
    }

    @Test fun `pagination failure has its own retry and preserves existing songs`() = runTest {
        val vm = model()
        vm.search("artist")
        runCurrent()
        assertEquals(25, vm.state.value.songs.size)
        catalog.search = { _, _ -> throw IOException("offline") }
        vm.loadMore()
        runCurrent()
        assertTrue(vm.state.value.appendFailed)
        assertFalse(vm.state.value.loadingMore)
        assertEquals(25, vm.state.value.songs.size)
        catalog.search = { _, limit -> CatalogSearch((1..limit).map { song(it.toLong()) }, limit) }
        vm.loadMore()
        runCurrent()
        assertEquals(50, vm.state.value.songs.size)
        assertFalse(vm.state.value.appendFailed)
    }

    @Test fun `clearing search restores history and cancels network work`() = runTest {
        store.recordPlayed(song(7), 1)
        catalog.search = { _, _ -> awaitCancellation() }
        val vm = model()
        vm.search("artist")
        runCurrent()
        vm.editInput("")
        runCurrent()
        assertEquals("", vm.state.value.query)
        assertEquals(listOf(7L), vm.state.value.songs.map { it.id })
        assertFalse(vm.state.value.loading)
    }

    @Test fun `restored query loads its persisted results and retains edited input`() = runTest {
        store.saveSearch("artist", SearchSnapshot(listOf(song()), 25))
        catalog.search = { _, _ -> awaitCancellation() }
        val vm = model(SavedStateHandle(mapOf(SavedStateKeys.SEARCH_QUERY to "Artist", SavedStateKeys.SEARCH_INPUT to "Artist edited")))
        runCurrent()
        assertEquals("Artist edited", vm.state.value.input)
        assertEquals("Artist", vm.state.value.query)
        assertEquals(listOf(song()), vm.state.value.songs)
    }

    @Test fun `history failure remains retryable after searching and clearing`() = runTest {
        var failed = true
        var subscriptions = 0
        val source = object : SongRepository by repository {
            override fun observeRecentSongs() = flow {
                subscriptions++
                if (failed) throw DataFailureException(DataFailure.STORAGE, IOException("storage unavailable"))
                emit(listOf(song(7)))
            }
        }
        val vm = SongsViewModel(source, SavedStateHandle()).also { models.put("songs", it) }
        runCurrent()
        assertEquals(UiFailure.UNEXPECTED, vm.state.value.failure)
        vm.search("artist")
        runCurrent()
        assertNull(vm.state.value.failure)
        vm.clearSearch()
        assertEquals(UiFailure.UNEXPECTED, vm.state.value.failure)
        failed = false
        vm.refresh()
        runCurrent()
        assertEquals(2, subscriptions)
        assertNull(vm.state.value.failure)
        assertEquals(listOf(7L), vm.state.value.songs.map { it.id })
    }
}
