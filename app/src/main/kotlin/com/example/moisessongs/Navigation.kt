package com.example.moisessongs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.ReportDrawnWhen
import androidx.core.net.toUri
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.moisessongs.domain.Song
import com.example.moisessongs.domain.SongRepository
import com.example.moisessongs.ui.album.AlbumScreen
import com.example.moisessongs.ui.album.AlbumViewModel
import com.example.moisessongs.ui.common.SongOptionsSheet
import com.example.moisessongs.ui.common.SplashScreen
import com.example.moisessongs.ui.player.PlayerScreen
import com.example.moisessongs.ui.player.PlayerViewModel
import com.example.moisessongs.ui.songs.SongsScreen
import com.example.moisessongs.ui.songs.SongsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@Composable
fun MainNavigation(repository: SongRepository, player: PlayerViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val backStack = rememberNavBackStack(Splash)
    ReportDrawnWhen { backStack.firstOrNull() != Splash }
    var optionsId by rememberSaveable { mutableStateOf<Long?>(null) }
    var optionsSong by remember { mutableStateOf<Song?>(null) }
    val navigate: (NavKey) -> Unit = { route ->
        val index = backStack.indexOf(route)
        if (index >= 0) while (backStack.lastIndex > index) backStack.removeAt(backStack.lastIndex)
        else backStack.add(route)
    }
    val goBack: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    val openSong: (Song, List<Song>) -> Unit = { song, songs -> player.select(song, songs); navigate(Player) }
    val openOptions: (Song) -> Unit = { song -> optionsSong = song; optionsId = song.id }
    LaunchedEffect(optionsId) {
        val id = optionsId
        if (id == null) optionsSong = null
        else if (optionsSong?.id != id) {
            try { optionsSong = repository.getSong(id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { optionsId = null }
        }
    }
    NavDisplay(backStack = backStack, onBack = goBack, modifier = modifier,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<Splash> {
                SplashScreen()
                LaunchedEffect(Unit) {
                    try { repository.observeRecentSongs().first() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Home presents storage errors and retry. */ }
                    backStack[0] = Songs
                }
            }
            entry<Songs> {
                val model: SongsViewModel = viewModel(factory = viewModelFactory {
                    initializer { SongsViewModel(repository, createSavedStateHandle()) }
                })
                val state by model.state.collectAsStateWithLifecycle()
                SongsScreen(state, model::editInput, model::submit, model::clearSearch, model::refresh,
                    model::loadMore, { model.search("Daft Punk") }, { openSong(it, model.state.value.songs) }, openOptions)
            }
            entry<Player> {
                val playback by player.presentation.collectAsStateWithLifecycle()
                val queue by player.queue.collectAsStateWithLifecycle()
                val historyFailed by player.historyFailed.collectAsStateWithLifecycle()
                PlayerScreen(playback, queue, historyFailed, goBack, openOptions, player::togglePlayback,
                    player::previous, player::next, player::seek, player::toggleRepeat, onStore = { url ->
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                        catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, R.string.open_link_error, Toast.LENGTH_SHORT).show()
                        }
                    }, progress = player.progress)
            }
            entry<AlbumRoute> { route ->
                val model: AlbumViewModel = viewModel(factory = viewModelFactory {
                    initializer { AlbumViewModel(route.id, repository) }
                })
                val state by model.state.collectAsStateWithLifecycle()
                AlbumScreen(state, goBack, model::refresh) { openSong(it, model.state.value.album?.songs.orEmpty()) }
            }
        })
    if (optionsId != null) optionsSong?.let { song ->
        SongOptionsSheet(song, { optionsId = null }) { albumId ->
            optionsId = null
            navigate(AlbumRoute(albumId))
        }
    }
}
