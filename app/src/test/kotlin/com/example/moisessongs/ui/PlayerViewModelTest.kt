package com.example.moisessongs.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.moisessongs.*
import com.example.moisessongs.data.DefaultSongRepository
import com.example.moisessongs.ui.common.SavedStateKeys
import com.example.moisessongs.ui.player.PlayerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = MemorySongStore()
    private val repository = DefaultSongRepository(FakeCatalog(), store, computationDispatcher = main.dispatcher)
    private val player = FakePlayer()
    private val models = ViewModelStore()
    private fun model(saved: SavedStateHandle = SavedStateHandle()) = PlayerViewModel(repository, saved) { player }
        .also { models.put("player", it) }
    @After fun cleanup() = models.clear()

    @Test fun `history is recorded after playback starts rather than on selection or buffering`() = runTest {
        val vm = model()
        runCurrent()
        vm.select(song(), listOf(song()))
        runCurrent()
        assertTrue(repository.observeRecentSongs().first().isEmpty())
        player.mutableState.value = player.state.value.copy(buffering = true)
        runCurrent()
        assertTrue(repository.observeRecentSongs().first().isEmpty())
        player.mutableState.value = player.state.value.copy(buffering = false, playing = true, durationMs = 30_000)
        runCurrent()
        assertEquals(listOf(song()), repository.observeRecentSongs().first())
        assertEquals(30_000L, vm.playback.value.durationMs)
        assertNotEquals(song().durationMs, vm.playback.value.durationMs)
    }

    @Test fun `queue controls honor boundaries and select adjacent songs`() = runTest {
        val vm = model()
        runCurrent()
        vm.select(song(1), listOf(song(1), song(2)))
        vm.previous()
        assertEquals(1L, vm.playback.value.song!!.id)
        vm.next()
        assertEquals(2L, vm.playback.value.song!!.id)
        vm.next()
        assertEquals(2L, vm.playback.value.song!!.id)
        vm.previous()
        assertEquals(1L, vm.playback.value.song!!.id)
    }

    @Test fun `restored selection never autoplays and explicit selection wins a restore race`() = runTest {
        store.recordPlayed(song(1), 1)
        val vm = model(SavedStateHandle(mapOf(SavedStateKeys.SELECTED_SONG_ID to 1L)))
        vm.select(song(2), listOf(song(2)))
        runCurrent()
        assertEquals(2L, vm.playback.value.song!!.id)
        assertEquals(1, player.preparations.size)
    }

    @Test fun `restored song is paused and player is released with the view model`() = runTest {
        store.recordPlayed(song(), 1)
        val vm = model(SavedStateHandle(mapOf(SavedStateKeys.SELECTED_SONG_ID to 1L)))
        runCurrent()
        assertEquals(song(), vm.playback.value.song)
        assertFalse(player.preparations.single().second)
        models.clear()
        assertTrue(player.released)
    }

    @Test fun `progress ticks do not emit new player presentation states`() = runTest {
        val vm = model()
        runCurrent()
        vm.select(song(), listOf(song()))
        runCurrent()
        val presentations = mutableListOf<com.example.moisessongs.domain.playback.PlaybackState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.presentation.toList(presentations) }
        repeat(100) { index ->
            player.mutableState.value = player.state.value.copy(positionMs = index * 250L)
            runCurrent()
        }
        assertEquals(1, presentations.size)
        assertEquals(0L, presentations.single().positionMs)
        player.mutableState.value = player.state.value.copy(playing = true)
        runCurrent()
        assertEquals(2, presentations.size)
        job.cancel()
    }
}
