package com.example.moisessongs.data.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.moisessongs.R
import com.example.moisessongs.domain.DataFailure
import com.example.moisessongs.domain.DataFailureException
import com.example.moisessongs.domain.SearchPolicy
import com.example.moisessongs.domain.SongRepository
import com.example.moisessongs.domain.library.MediaIds
import com.example.moisessongs.domain.library.MediaLibrary
import com.example.moisessongs.domain.library.libraryPage
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.guava.await

@androidx.annotation.OptIn(UnstableApi::class)
internal class SongLibraryCallback(
    private val context: Context,
    repository: SongRepository,
    private val scope: CoroutineScope,
) : MediaLibrarySession.Callback {
    private companion object { const val MAX_QUERY_LENGTH = 200 }
    private val library = MediaLibrary(repository)

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        if (controller.packageName != context.packageName && !controller.isTrusted) {
            return MediaSession.ConnectionResult.reject()
        }
        if (controller.packageName != context.packageName) {
            context.grantUriPermission(controller.packageName, ArtworkUris.root(context),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(SessionKeys.TOGGLE_REPEAT, Bundle.EMPTY)).build()).build()
    }

    override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
        if (customCommand.customAction != SessionKeys.TOGGLE_REPEAT) {
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
        session.player.repeatMode = if (session.player.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE)
            androidx.media3.common.Player.REPEAT_MODE_OFF else androidx.media3.common.Player.REPEAT_MODE_ONE
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
        params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
        val hints = Bundle().apply {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        }
        return Futures.immediateFuture(LibraryResult.ofItem(libraryFolder(context, MediaIds.ROOT, context.getString(R.string.songs)),
            LibraryParams.Builder().setExtras(hints).build()))
    }

    override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
        mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = result {
        val item = when (mediaId) {
            MediaIds.ROOT -> libraryFolder(context, mediaId, context.getString(R.string.songs))
            MediaIds.RECENT -> libraryFolder(context, mediaId, context.getString(R.string.recently_played))
            MediaIds.ALBUMS -> libraryFolder(context, mediaId, context.getString(R.string.albums))
            else -> MediaIds.songId(mediaId)?.let { library.song(it).toMediaItem(context) }
                ?: MediaIds.albumId(mediaId)?.let { id -> library.album(id).let {
                    libraryFolder(context, mediaId, it.title, it.artworkUrl, it.artist)
                } } ?: throw IllegalArgumentException("Unknown media ID")
        }
        LibraryResult.ofItem(item, null)
    }

    override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
        parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = result {
        val items = when (parentId) {
            MediaIds.ROOT -> listOf(libraryFolder(context, MediaIds.RECENT, context.getString(R.string.recently_played)),
                libraryFolder(context, MediaIds.ALBUMS, context.getString(R.string.albums)))
            MediaIds.RECENT -> library.recent().filter { it.previewUrl != null }.map { it.toMediaItem(context) }
            MediaIds.ALBUMS -> library.albums().map {
                libraryFolder(context, MediaIds.album(requireNotNull(it.albumId)), it.album, it.artworkUrl, it.artist)
            }
            else -> library.album(requireNotNull(MediaIds.albumId(parentId))).songs
                .filter { it.previewUrl != null }.map { it.toMediaItem(context) }
        }
        LibraryResult.ofItemList(items.libraryPage(page, pageSize), params)
    }

    override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
        query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> = result {
        validateQuery(query)
        val songs = try { library.search(query) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { library.searchResults(query).ifEmpty { throw failure } }
        session.notifySearchResultChanged(browser, query, songs.size, params)
        LibraryResult.ofVoid(params)
    }

    override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
        query: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = result {
        LibraryResult.ofItemList(library.searchResults(query).libraryPage(page, pageSize).map { it.toMediaItem(context) }, params)
    }

    override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> = scope.future {
        require(mediaItems.isNotEmpty() && mediaItems.size <= SearchPolicy.MAX_RESULTS)
        mediaItems.map { item ->
            val song = library.song(requireNotNull(MediaIds.songId(item.mediaId)))
            require(song.previewUrl != null) { context.getString(R.string.preview_unavailable) }
            song.toMediaItem(context)
        }
    }

    override fun onSetMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        if (mediaItems.size == 1) {
            val item = mediaItems.single()
            val search = item.requestMetadata.searchQuery
            val albumId = MediaIds.albumId(item.mediaId)
            val songId = MediaIds.songId(item.mediaId)
            val songs = if (search != null) {
                validateQuery(search)
                try { library.search(search) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { library.searchResults(search).ifEmpty { throw failure } }
            } else if (albumId != null) library.album(albumId).songs.filter { it.previewUrl != null }
                else library.queueFor(library.song(requireNotNull(songId)))
            require(songs.isNotEmpty()) { context.getString(R.string.preview_unavailable) }
            val index = if (songId == null) 0 else songs.indexOfFirst { it.id == songId }
            require(index >= 0)
            MediaSession.MediaItemsWithStartPosition(songs.map { it.toMediaItem(context) }, index,
                if (startPositionMs == C.TIME_UNSET) 0 else startPositionMs.coerceAtLeast(0))
        } else {
            val items = onAddMediaItems(mediaSession, controller, mediaItems).await()
            MediaSession.MediaItemsWithStartPosition(items, startIndex, startPositionMs)
        }
    }

    private fun validateQuery(query: String) {
        require(SearchPolicy.normalize(query).isNotBlank() && query.length <= MAX_QUERY_LENGTH)
    }

    private fun <T : Any> result(block: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> = scope.future<LibraryResult<T>> {
        try { return@future block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) { return@future LibraryResult.ofError<T>(SessionError(SessionError.ERROR_BAD_VALUE, context.getString(R.string.media_unavailable))) }
        catch (failure: Exception) {
            val message = if ((failure as? DataFailureException)?.failure == DataFailure.CONNECTION)
                R.string.connection_error else R.string.unexpected_error
            return@future LibraryResult.ofError<T>(SessionError(SessionError.ERROR_IO, context.getString(message)))
        }
    }
}
