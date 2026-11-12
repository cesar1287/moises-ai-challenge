package com.example.moisessongs

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.example.moisessongs.data.local.RoomSongStore
import com.example.moisessongs.data.local.SongDatabase
import com.example.moisessongs.domain.Album
import com.example.moisessongs.domain.SearchSnapshot
import com.example.moisessongs.domain.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

internal fun testSong(id: Long = 1) = Song(id, if (id == 1L) "Get Lucky" else "One More Time", "Daft Punk", 10,
    "Discovery", null, "https://example.com/$id.m4a", null, 240_000, id.toInt())

@RunWith(AndroidJUnit4::class)
class RoomSongStoreTest {
    private lateinit var database: SongDatabase
    private lateinit var store: RoomSongStore
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, SongDatabase::class.java).build()
        store = RoomSongStore(database)
    }
    @After fun close() = database.close()

    @Test fun orderedResultsAreIsolatedByQueryAndRefreshRemovesOldMembership() = runBlocking {
        store.saveSearch("first", SearchSnapshot(listOf(testSong(2), testSong(1)), 25, true, 1))
        store.saveSearch("second", SearchSnapshot(listOf(testSong(1)), 25, true, 1))
        assertEquals(listOf(2L, 1L), store.observeSearch("first").first()!!.songs.map { it.id })
        store.saveSearch("first", SearchSnapshot(listOf(testSong(3)), 25, true, 2))
        assertEquals(listOf(3L), store.observeSearch("first").first()!!.songs.map { it.id })
        assertEquals(listOf(1L), store.observeSearch("second").first()!!.songs.map { it.id })
        assertEquals(testSong(2), store.song(2))
    }

    @Test fun failedRefreshRollsBackSongWritesMembershipAndMetadata() = runBlocking {
        val before = SearchSnapshot(listOf(testSong()), 25, false, 1)
        store.saveSearch("artist", before)
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_refresh BEFORE INSERT ON searches WHEN NEW.updatedAt = 2
            BEGIN SELECT RAISE(ABORT, 'simulated storage failure'); END
        """.trimIndent())
        try {
            store.saveSearch("artist", SearchSnapshot(listOf(testSong(99)), 50, true, 2))
            fail("Expected transaction failure")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before, store.observeSearch("artist").first())
        assertNull(store.song(99))
    }

    @Test fun successfulEmptySearchIsDistinctFromUncachedQuery() = runBlocking {
        assertNull(store.observeSearch("missing").first())
        store.saveSearch("empty", SearchSnapshot(emptyList(), 25, true, 1))
        assertEquals(SearchSnapshot(emptyList(), 25, true, 1), store.observeSearch("empty").first())
    }

    @Test fun recentHistoryIsBoundedUniqueAndRecencyOrdered() = runBlocking {
        for (id in 1L..25L) store.recordPlayed(testSong(id), id)
        assertEquals((25L downTo 6L).toList(), store.observeRecentSongs().first().map { it.id })
        store.recordPlayed(testSong(10), 30)
        val recent = store.observeRecentSongs().first()
        assertEquals(20, recent.size)
        assertEquals(10L, recent.first().id)
        assertEquals(1, recent.count { it.id == 10L })
    }

    @Test fun albumHasItsOwnTrackMembershipAndOrdering() = runBlocking {
        val album = Album(10, "Discovery", "Daft Punk", null, "2001", "Electronic", 2, null,
            listOf(testSong(2), testSong(1)))
        store.saveAlbum(album)
        store.saveSearch("artist", SearchSnapshot(listOf(testSong(1)), 25))
        assertEquals(album, store.observeAlbum(10).first())
    }

    @Test fun recordingAnOlderQueueEntryDoesNotOverwriteRefreshedCatalogMetadata() = runBlocking {
        val old = testSong()
        val refreshed = old.copy(title = "Updated catalog title")
        store.saveSearch("artist", SearchSnapshot(listOf(refreshed), 25))
        store.recordPlayed(old, 100)
        assertEquals(refreshed, store.song(old.id))
        assertEquals(listOf(refreshed), store.observeRecentSongs().first())
    }

    @Test fun searchAndListeningHistorySurviveDatabaseReopening() = runBlocking {
        val name = "persistence-${System.nanoTime()}.db"
        var disk = Room.databaseBuilder(context, SongDatabase::class.java, name).build()
        try {
            val initial = RoomSongStore(disk)
            initial.saveSearch("artist", SearchSnapshot(listOf(testSong()), 25, true, 1))
            initial.recordPlayed(testSong(), 1)
            disk.close()
            disk = Room.databaseBuilder(context, SongDatabase::class.java, name).build()
            val reopened = RoomSongStore(disk)
            assertEquals(listOf(testSong()), reopened.observeSearch("artist").first()!!.songs)
            assertEquals(listOf(testSong()), reopened.observeRecentSongs().first())
        } finally { disk.close(); context.deleteDatabase(name) }
    }
}
