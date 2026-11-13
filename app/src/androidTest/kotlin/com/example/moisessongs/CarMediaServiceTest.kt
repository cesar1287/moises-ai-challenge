package com.example.moisessongs

import android.content.ComponentName
import android.content.Intent
import android.media.browse.MediaBrowser as LegacyBrowser
import android.media.session.MediaController as LegacyController
import android.net.Uri
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.moisessongs.data.local.RoomSongStore
import com.example.moisessongs.data.local.toEntity
import com.example.moisessongs.data.playback.Media3PreviewPlayer
import com.example.moisessongs.data.playback.PlaybackService
import com.example.moisessongs.data.playback.SessionKeys
import com.example.moisessongs.data.playback.ArtworkUris
import com.example.moisessongs.domain.Album
import com.example.moisessongs.domain.CatalogSearch
import com.example.moisessongs.domain.Song
import com.example.moisessongs.domain.library.MediaIds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class CarMediaServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val application get() = context.applicationContext as InstrumentedSongsApplication
    private val repository get() = application.dependencies.repository
    private val phoneScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var browser: MediaBrowser
    private var phone: Media3PreviewPlayer? = null
    private var activity: ActivityScenario<MainActivity>? = null
    private lateinit var source: File
    private lateinit var first: Song
    private lateinit var second: Song

    @Before fun setup() = runBlocking<Unit> {
        application.database.clearAllTables()
        application.catalog.search = { _, _ -> throw IOException("offline") }
        application.catalog.lookup = { throw IOException("offline") }
        source = File(context.cacheDir, "car-test-preview.wav")
        writeSilentPreview(source)
        val uri = Uri.fromFile(source).toString()
        first = testSong(30_001).copy(title = "First song", previewUrl = uri, trackNumber = 1)
        second = testSong(30_002).copy(title = "Second song", previewUrl = uri, trackNumber = 2)
        application.database.songs().putSongs(listOf(first.toEntity(), second.toEntity()))
        RoomSongStore(application.database).saveAlbum(Album(10, "Discovery", "Daft Punk", null,
            "2001", "Electronic", 2, null, listOf(first, second)))
        activity = ActivityScenario.launch(MainActivity::class.java)
        browser = withContext(Dispatchers.Main) {
            MediaBrowser.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
                .buildAsync().await()
        }
    }

    @After fun cleanup() {
        // Settle playback/notification work before tearing down the service for the next test.
        instrumentation.runOnMainSync { browser.stop(); browser.clearMediaItems() }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { phone?.release(); browser.release() }
        phoneScope.cancel()
        activity?.close()
        context.stopService(Intent(context, PlaybackService::class.java))
        instrumentation.waitForIdleSync()
        source.delete()
    }

    @Test fun cachedRecentSongsAndAlbumsAreBrowsableWithPagingAndAlbumLinks() = runBlocking<Unit> {
        repository.recordPlayed(first)
        repository.recordPlayed(second)
        val root = withContext(Dispatchers.Main) { browser.getLibraryRoot(null).await() }
        assertEquals(LibraryResult.RESULT_SUCCESS, root.resultCode)
        assertEquals(MediaIds.ROOT, root.value!!.mediaId)
        assertEquals(listOf(MediaIds.RECENT, MediaIds.ALBUMS), children(MediaIds.ROOT).value!!.map { it.mediaId })
        assertEquals(listOf(MediaIds.song(second.id)), children(MediaIds.RECENT, 0, 1).value!!.map { it.mediaId })
        assertEquals(listOf(MediaIds.song(first.id)), children(MediaIds.RECENT, 1, 1).value!!.map { it.mediaId })
        assertEquals(listOf(MediaIds.album(10)), children(MediaIds.ALBUMS).value!!.map { it.mediaId })
        val album = children(MediaIds.album(10)).value!!
        assertEquals(listOf(MediaIds.song(first.id), MediaIds.song(second.id)), album.map { it.mediaId })
        assertEquals(MediaIds.album(10), album.first().mediaMetadata.extras!!.getString(SessionKeys.ALBUM_LINK))
        assertEquals(SessionError.ERROR_BAD_VALUE, children("unknown").resultCode)
    }

    @Test fun carArtworkUsesReadOnlyLocalUrisAndResolvesOnlyCachedArtwork() = runBlocking<Unit> {
        val artwork = "android.resource://${context.packageName}/${R.drawable.musical_note}"
        application.database.songs().putSongs(listOf(first.copy(artworkUrl = artwork).toEntity()))
        repository.recordPlayed(first.copy(artworkUrl = artwork))
        val item = children(MediaIds.RECENT).value!!.first()
        assertEquals(ArtworkUris.song(context, first.id), item.mediaMetadata.artworkUri)
        // Album covers work from listening history before the full album has been fetched.
        for (uri in listOf(item.mediaMetadata.artworkUri!!, ArtworkUris.album(context, first.albumId!!))) {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertNotNull(bitmap)
            assertTrue(bitmap.width <= 600 && bitmap.height <= 600)
            bitmap.recycle()
        }
        assertThrows(FileNotFoundException::class.java) {
            context.contentResolver.openFileDescriptor(ArtworkUris.song(context, first.id), "w")
        }
        assertThrows(FileNotFoundException::class.java) {
            context.contentResolver.openInputStream(ArtworkUris.root(context).buildUpon().appendPath("private-file").build())
        }
    }

    @Test fun carAndPhoneSharePlaybackAndClosingThePhoneDoesNotStopTheCar() = runBlocking<Unit> {
        withContext(Dispatchers.Main) {
            // A client URI is ignored: the service resolves its own cached song by ID.
            browser.setMediaItem(MediaItem.Builder().setMediaId(MediaIds.song(first.id))
                .setUri("file:///untrusted-client-uri.wav").build())
            browser.prepare()
            browser.play()
            phone = Media3PreviewPlayer(context, phoneScope)
        }
        awaitBrowser { it.isPlaying && it.currentMediaItem?.mediaId == MediaIds.song(first.id) }
        withTimeout(15.seconds) { phone!!.state.first { it.playing && it.song?.id == first.id } }
        assertEquals(20_000L, withContext(Dispatchers.Main) { browser.duration })
        withContext(Dispatchers.Main) { phone!!.prepare(second, autoPlay = false) }
        withTimeout(15.seconds) { phone!!.state.first { it.song?.id == first.id && it.playing } }
        withContext(Dispatchers.Main) { phone!!.pause() }
        awaitBrowser { !it.isPlaying }
        withContext(Dispatchers.Main) { phone!!.seekTo(8_000) }
        val repeat = withContext(Dispatchers.Main) {
            browser.sendCustomCommand(SessionCommand(SessionKeys.TOGGLE_REPEAT, Bundle.EMPTY), Bundle.EMPTY).await()
        }
        assertEquals(SessionResult.RESULT_SUCCESS, repeat.resultCode)
        awaitBrowser { it.currentPosition >= 7_900 && it.repeatMode == Player.REPEAT_MODE_ONE }
        withContext(Dispatchers.Main) { browser.play() }
        awaitBrowser { it.isPlaying }
        withContext(Dispatchers.Main) { phone!!.release() }
        phone = null
        activity!!.close()
        activity = null
        assertTrue(withContext(Dispatchers.Main) { browser.isPlaying })
        withContext(Dispatchers.Main) { browser.seekToNextMediaItem() }
        awaitBrowser { it.currentMediaItem?.mediaId == MediaIds.song(second.id) && it.isPlaying }
        assertEquals("Second song", withContext(Dispatchers.Main) { browser.mediaMetadata.title.toString() })
    }

    @Test fun serviceRecordsActualPlaybackAndRecoversAFailedHistoryWrite() = runBlocking<Unit> {
        val sql = application.database.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER fail_car_history BEFORE INSERT ON recent_plays BEGIN SELECT RAISE(ABORT, 'test write failure'); END")
        try {
            withContext(Dispatchers.Main) {
                browser.setMediaItem(MediaItem.Builder().setMediaId(MediaIds.song(first.id)).build())
                browser.prepare()
            }
            awaitBrowser { it.playbackState == Player.STATE_READY }
            assertTrue(repository.observeRecentSongs().first().isEmpty())
            withContext(Dispatchers.Main) { browser.play() }
            awaitBrowser { it.isPlaying && it.sessionExtras.getBoolean(SessionKeys.HISTORY_FAILED) }
            assertTrue(repository.observeRecentSongs().first().isEmpty())
            sql.execSQL("DROP TRIGGER fail_car_history")
            withContext(Dispatchers.Main) { browser.pause() }
            awaitBrowser { !it.isPlaying }
            withContext(Dispatchers.Main) { browser.play() }
            withTimeout(15.seconds) { repository.observeRecentSongs().first { songs -> songs.any { it.id == first.id } } }
            awaitBrowser { !it.sessionExtras.getBoolean(SessionKeys.HISTORY_FAILED) }
        } finally { sql.execSQL("DROP TRIGGER IF EXISTS fail_car_history") }
    }

    @Test fun voiceSearchAndOfflineSearchResultsUseTheSameCachedCatalog() = runBlocking<Unit> {
        var searched = ""
        application.catalog.search = { query, _ -> searched = query; CatalogSearch(listOf(first, second), 2) }
        val result = withContext(Dispatchers.Main) { browser.search("Daft Punk", null).await() }
        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode)
        assertEquals("Daft Punk", searched)
        val songs = withContext(Dispatchers.Main) { browser.getSearchResult("Daft Punk", 0, 10, null).await() }
        assertEquals(listOf(MediaIds.song(first.id), MediaIds.song(second.id)), songs.value!!.map { it.mediaId })
        application.catalog.search = { _, _ -> throw IOException("offline") }
        assertEquals(LibraryResult.RESULT_SUCCESS, withContext(Dispatchers.Main) { browser.search("Daft Punk", null).await() }.resultCode)
        withContext(Dispatchers.Main) {
            browser.setMediaItem(MediaItem.Builder().setRequestMetadata(MediaItem.RequestMetadata.Builder()
                .setSearchQuery("Daft Punk").build()).build())
            browser.prepare()
            browser.play()
        }
        awaitBrowser { it.isPlaying && it.currentMediaItem?.mediaId == MediaIds.song(first.id) }
    }

    @Test fun legacyBrowserCanDiscoverTheLibraryAndStartPlaybackByMediaId() = runBlocking<Unit> {
        val connected = CompletableDeferred<Unit>()
        val loaded = CompletableDeferred<List<LegacyBrowser.MediaItem>>()
        val legacy = withContext(Dispatchers.Main) { LegacyBrowser(context, ComponentName(context, PlaybackService::class.java),
            object : LegacyBrowser.ConnectionCallback() {
                override fun onConnected() { connected.complete(Unit) }
                override fun onConnectionFailed() { connected.completeExceptionally(IllegalStateException("Browser connection failed")) }
            }, null) }
        try {
            withContext(Dispatchers.Main) { legacy.connect() }
            withTimeout(15.seconds) { connected.await() }
            withContext(Dispatchers.Main) {
                legacy.subscribe(MediaIds.ROOT, object : LegacyBrowser.SubscriptionCallback() {
                    override fun onChildrenLoaded(parentId: String, children: MutableList<LegacyBrowser.MediaItem>) {
                        loaded.complete(children)
                    }
                })
            }
            assertEquals(listOf(MediaIds.RECENT, MediaIds.ALBUMS), withTimeout(15.seconds) { loaded.await() }.map { it.mediaId })
            withContext(Dispatchers.Main) {
                LegacyController(context, legacy.sessionToken).transportControls.playFromMediaId(MediaIds.song(first.id), Bundle.EMPTY)
            }
            awaitBrowser { it.isPlaying && it.currentMediaItem?.mediaId == MediaIds.song(first.id) }
        } finally { withContext(Dispatchers.Main) { legacy.disconnect() } }
    }

    private suspend fun children(id: String, page: Int = 0, pageSize: Int = 100) = withContext(Dispatchers.Main) {
        browser.getChildren(id, page, pageSize, null).await()
    }

    private suspend fun awaitBrowser(predicate: (MediaBrowser) -> Boolean) = withContext(Dispatchers.Main) {
        withTimeout(15.seconds) { while (!predicate(browser)) delay(10.milliseconds) }
    }

    private fun writeSilentPreview(file: File) {
        val sampleRate = 16_000
        val dataSize = sampleRate * 2 * 20
        val wav = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
        wav.putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize)
        file.writeBytes(wav.array())
    }
}
