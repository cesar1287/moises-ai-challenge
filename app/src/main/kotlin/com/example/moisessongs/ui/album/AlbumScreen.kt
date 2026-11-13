package com.example.moisessongs.ui.album

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.moisessongs.R
import com.example.moisessongs.domain.Song
import com.example.moisessongs.ui.common.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(state: AlbumUiState, onBack: () -> Unit, onRefresh: () -> Unit, onSong: (Song) -> Unit) {
    val album = state.album
    val listState = rememberLazyListState()
    val refreshDescription = stringResource(R.string.refresh_album)
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).widthIn(max = 720.dp)) {
            ScreenHeader(album?.title ?: stringResource(R.string.album), onBack)
            PullToRefreshBox(isRefreshing = state.loading && album != null, onRefresh = onRefresh,
                modifier = Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().semantics {
                    customActions = listOf(CustomAccessibilityAction(refreshDescription) { onRefresh(); true })
                }, contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (state.failure != null) item {
                        FailureBanner(state.failure, if (album != null) stringResource(R.string.saved_album) else null, onRefresh)
                    }
                    if (album == null && state.loading) item { LoadingState(stringResource(R.string.loading_album)) }
                    if (album != null) {
                        item(key = ListItemKeys.ALBUM_HEADER) {
                            Column(Modifier.fillMaxWidth().padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Artwork(album.artworkUrl, 120.dp, radius = 20.dp)
                                Spacer(Modifier.height(16.dp))
                                Text(album.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(album.artist, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp))
                            }
                        }
                        items(album.songs, key = Song::id, contentType = { ListContentTypes.SONG }) { song -> SongRow(song, { onSong(song) }, compact = true) }
                        if (album.songs.isEmpty()) item { EmptyState(stringResource(R.string.no_results), stringResource(R.string.album_unavailable)) }
                    }
                }
            }
        }
    }
}
