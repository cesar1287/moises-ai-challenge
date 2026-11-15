package com.example.moisessongs.ui.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.example.moisessongs.R
import com.example.moisessongs.domain.SearchPolicy
import com.example.moisessongs.domain.Song
import com.example.moisessongs.ui.common.*
import com.example.moisessongs.ui.theme.AppCorners
import com.example.moisessongs.ui.theme.AppLayout
import com.example.moisessongs.ui.theme.AppSizes
import com.example.moisessongs.ui.theme.AppSpacing
import com.example.moisessongs.ui.theme.MutedText
import com.example.moisessongs.ui.theme.SearchSurface
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    state: SongsUiState,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSuggestion: () -> Unit,
    onSong: (Song) -> Unit,
    onOptions: (Song) -> Unit,
) {
    var searchExpanded by rememberSaveable { mutableStateOf(true) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var focusSearch by remember { mutableStateOf(false) }
    LaunchedEffect(searchExpanded, focusSearch) {
        if (searchExpanded && focusSearch) {
            focusRequester.requestFocus()
            keyboard?.show()
            focusSearch = false
        }
    }
    val refreshDescription = stringResource(R.string.refresh_songs)
    val listState = rememberLazyListState()
    var displayedQuery by rememberSaveable { mutableStateOf(state.query) }
    LaunchedEffect(state.query) {
        if (displayedQuery != state.query) { listState.scrollToItem(0); displayedQuery = state.query }
    }
    LaunchedEffect(state.query, state.songs.size, state.loading, state.loadingMore, state.failure) {
        if (state.query.isNotBlank() && state.snapshot != null && !state.snapshot.endReached &&
            !state.loading && !state.loadingMore && state.failure == null) {
            snapshotFlow {
                val layout = listState.layoutInfo
                layout.totalItemsCount > 0 && (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - 3
            }.first { it }
            onLoadMore()
        }
    }
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).widthIn(max = AppLayout.contentMaxWidth)) {
            ScreenHeader(stringResource(R.string.songs)) {
                if (!searchExpanded) AssetButton(R.drawable.ic_search_white, stringResource(R.string.search), {
                    searchExpanded = true
                    focusSearch = true
                })
            }
            if (searchExpanded) {
                val description = stringResource(R.string.search_songs)
                Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large, vertical = AppSpacing.small)
                    .heightIn(min = AppSizes.touchTarget).background(SearchSurface, RoundedCornerShape(AppCorners.medium))
                    .padding(start = AppSpacing.regular), verticalAlignment = Alignment.CenterVertically) {
                    FigmaIcon(R.drawable.ic_search)
                    Spacer(Modifier.width(AppSpacing.small))
                    BasicTextField(value = state.input, onValueChange = onInput, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focus.clearFocus(); keyboard?.hide(); onSubmit(); searchExpanded = false
                        }),
                        modifier = Modifier.weight(1f).padding(vertical = AppSpacing.inputVertical).focusRequester(focusRequester)
                            .testTag(UiTestTags.SEARCH_INPUT).semantics { contentDescription = description },
                        decorationBox = { field ->
                            Box {
                                if (state.input.isEmpty()) Text(stringResource(R.string.search), color = MutedText,
                                    style = MaterialTheme.typography.bodyLarge)
                                field()
                            }
                        })
                    if (state.input.isNotEmpty()) IconButton(onClick = { onClear(); searchExpanded = true }) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_search), Modifier.size(AppSizes.smallIcon), tint = MutedText)
                    } else Spacer(Modifier.width(AppSpacing.regular))
                }
            }
            PullToRefreshBox(isRefreshing = state.loading && state.songs.isNotEmpty(), onRefresh = onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag(UiTestTags.SONG_LIST).semantics {
                    customActions = listOf(CustomAccessibilityAction(refreshDescription) { onRefresh(); true })
                },
                    contentPadding = PaddingValues(bottom = AppSpacing.screen)) {
                    if (state.failure != null) item(key = ListItemKeys.FAILURE) {
                        FailureBanner(state.failure,
                            savedLabel = if (state.songs.isNotEmpty()) stringResource(R.string.saved_results) else null,
                            onRetry = if (state.appendFailed) onLoadMore else onRefresh)
                    }
                    if (state.query.isBlank() && state.recentSongs.isNotEmpty()) item(key = ListItemKeys.RECENT_HEADING) {
                        Text(stringResource(R.string.recently_played), color = MutedText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = AppSpacing.screen, vertical = AppSpacing.medium).semantics { heading() })
                    }
                    if (state.query.isBlank() && state.recentSongs.isEmpty()) item(key = ListItemKeys.WELCOME) {
                        EmptyState(stringResource(R.string.discover_title), stringResource(R.string.discover_body),
                            stringResource(R.string.try_search, SearchPolicy.SUGGESTED_QUERY), onSuggestion)
                    }
                    if (state.loading && state.songs.isEmpty()) item(key = ListItemKeys.LOADING) {
                        LoadingState(stringResource(R.string.loading))
                    }
                    if (state.query.isNotBlank() && !state.loading && state.snapshot != null && state.songs.isEmpty()) {
                        item(key = ListItemKeys.EMPTY) { EmptyState(stringResource(R.string.no_results), stringResource(R.string.no_results_body)) }
                    }
                    items(state.songs, key = Song::id, contentType = { ListContentTypes.SONG }) { song ->
                        SongRow(song, onClick = { onSong(song) }, onOptions = { onOptions(song) })
                    }
                    if (state.query.isNotBlank() && state.songs.isNotEmpty()) item(key = ListItemKeys.PAGINATION) {
                        when {
                            state.loadingMore -> LoadingState(stringResource(R.string.loading))
                            state.snapshot?.endReached == true -> Text(
                                stringResource(if (state.snapshot.requestedLimit >= SearchPolicy.MAX_RESULTS)
                                    R.string.search_limit else R.string.end_results),
                                style = MaterialTheme.typography.bodySmall, color = MutedText,
                                modifier = Modifier.fillMaxWidth().padding(AppSpacing.screen),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            else -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextButton(onClick = onLoadMore, enabled = !state.loading) { Text(stringResource(R.string.load_more)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
