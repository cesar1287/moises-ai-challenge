package com.example.moisessongs

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.moisessongs.domain.*
import com.example.moisessongs.domain.playback.PlaybackState
import com.example.moisessongs.domain.playback.PreviewPlayer
import com.example.moisessongs.ui.player.PlayerViewModel
import com.example.moisessongs.ui.theme.MoisesSongsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongsJourneyTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val models = ViewModelStore()
    private val repository = JourneyRepository()
    private val playback = JourneyPlayer()
    private fun content(restoration: StateRestorationTester? = null) {
        lateinit var player: PlayerViewModel
        rule.runOnUiThread {
            player = PlayerViewModel(repository, SavedStateHandle()) { playback }
            models.put("player", player)
        }
        val ui: @androidx.compose.runtime.Composable () -> Unit = {
            MoisesSongsTheme { MainNavigation(repository, player) }
        }
        if (restoration != null) restoration.setContent(ui) else rule.setContent(ui)
    }
    @After fun cleanup() { rule.runOnUiThread { models.clear() } }

    private fun search() {
        rule.onNodeWithContentDescription("Search songs and artists").performTextInput("Daft Punk")
        rule.onNodeWithContentDescription("Search songs and artists").performImeAction()
        rule.onNodeWithText("Get Lucky").assertIsDisplayed()
    }

    @Test fun searchPlayerOptionsAlbumTrackAndBackFormACompleteJourney() {
        content()
        search()
        rule.onNodeWithText("Get Lucky").performClick()
        rule.onNodeWithText("Now playing").assertIsDisplayed()
        rule.onNodeWithContentDescription("Preview timeline").assertIsDisplayed()
        rule.onNodeWithContentDescription("More options for Get Lucky").performClick()
        rule.onNodeWithText("View album").performClick()
        rule.onNodeWithText("One More Time").assertIsDisplayed().performClick()
        rule.onNodeWithText("Now playing").assertIsDisplayed()
        rule.onNodeWithText("One More Time").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Songs").assertIsDisplayed()
    }

    @Test fun selectionAndBackStackSurviveSavedStateRestoration() {
        val restoration = StateRestorationTester(rule)
        content(restoration)
        search()
        rule.onNodeWithText("Get Lucky").performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Now playing").assertIsDisplayed()
        rule.onNodeWithText("Get Lucky").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Songs").assertIsDisplayed()
        rule.onNodeWithText("Get Lucky").assertIsDisplayed()
    }

    @Test fun failedSearchOffersRetryAndCanRecoverWithoutReenteringText() {
        repository.fail = true
        content()
        rule.onNodeWithContentDescription("Search songs and artists").performTextInput("Daft Punk")
        rule.onNodeWithContentDescription("Search songs and artists").performImeAction()
        rule.onNodeWithText("Couldn’t connect. Check your internet connection and try again.").assertIsDisplayed()
        rule.runOnUiThread { repository.fail = false }
        rule.onNodeWithText("Try again").performClick()
        rule.onNodeWithText("Get Lucky").assertIsDisplayed()
        assertEquals("Daft Punk", repository.lastQuery)
    }

    @Test fun sheetDismissalLeavesThePlayerVisible() {
        content()
        search()
        rule.onNodeWithText("Get Lucky").performClick()
        rule.onNodeWithContentDescription("More options for Get Lucky").performClick()
        rule.onNodeWithText("View album").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithText("View album").assertDoesNotExist()
        rule.onNodeWithText("Now playing").assertIsDisplayed()
    }

    @Test fun selectingTheSamePausedSongFromHomeResumesItsPreview() {
        content()
        search()
        rule.onNodeWithText("Get Lucky").performClick()
        rule.onNodeWithContentDescription("Preview timeline").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.SetProgress
        ) { it(.4f) }
        rule.onNodeWithContentDescription("Pause preview").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Get Lucky").performClick()
        rule.onNodeWithContentDescription("Pause preview").assertIsDisplayed()
        rule.runOnIdle { assertEquals(12_000L, playback.state.value.positionMs) }
    }
}

private class JourneyRepository : SongRepository {
    private val songs = listOf(testSong(), testSong(2))
    private val search = MutableStateFlow<SearchSnapshot?>(null)
    private val recent = MutableStateFlow<List<Song>>(emptyList())
    private val album = MutableStateFlow<Album?>(null)
    var fail = false
    var lastQuery = ""
    override fun observeSearch(query: String): Flow<SearchSnapshot?> = search
    override fun observeRecentSongs(): Flow<List<Song>> = recent
    override fun observeAlbum(id: Long): Flow<Album?> = album
    override suspend fun loadSearch(query: String, refresh: Boolean) {
        lastQuery = query
        if (fail) throw DataFailureException(DataFailure.CONNECTION, java.io.IOException("offline"))
        search.value = SearchSnapshot(songs, 25, true, 1)
    }
    override suspend fun refreshAlbum(id: Long) {
        album.value = Album(10, "Discovery", "Daft Punk", null, "2001", "Electronic", 2, null, songs)
    }
    override suspend fun getSong(id: Long) = songs.firstOrNull { it.id == id }
    override suspend fun recordPlayed(song: Song) { recent.value = listOf(song) }
}

private class JourneyPlayer : PreviewPlayer {
    override val state = MutableStateFlow(PlaybackState())
    override val queue = MutableStateFlow<List<Song>>(emptyList())
    override fun prepare(song: Song, autoPlay: Boolean, queue: List<Song>) {
        this.queue.value = queue
        state.value = PlaybackState(song, playing = autoPlay, durationMs = 30_000)
    }
    override fun play() { state.value = state.value.copy(playing = true) }
    override fun togglePlayback() { state.value = state.value.copy(playing = !state.value.playing) }
    override fun seekTo(positionMs: Long) { state.value = state.value.copy(positionMs = positionMs) }
    override fun toggleRepeat() { state.value = state.value.copy(repeat = !state.value.repeat) }
    override fun pause() { state.value = state.value.copy(playing = false) }
    override fun release() { }
}
