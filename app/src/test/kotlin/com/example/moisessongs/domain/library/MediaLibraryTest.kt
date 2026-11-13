package com.example.moisessongs.domain.library

import com.example.moisessongs.FakeCatalog
import com.example.moisessongs.MemorySongStore
import com.example.moisessongs.album
import com.example.moisessongs.song
import com.example.moisessongs.data.DefaultSongRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MediaLibraryTest {
    private val catalog = FakeCatalog()
    private val store = MemorySongStore()
    private val library = MediaLibrary(DefaultSongRepository(catalog, store))

    @Test fun `media IDs reject unknown prefixes malformed values and nonpositive IDs`() {
        assertEquals(42L, MediaIds.songId(MediaIds.song(42)))
        assertEquals(15L, MediaIds.albumId(MediaIds.album(15)))
        listOf("", "song:0", "song:-1", "song:abc", "song:9223372036854775808", "album:42")
            .forEach { assertNull(MediaIds.songId(it)) }
        assertNull(MediaIds.albumId(MediaIds.ROOT))
    }

    @Test fun `pages keep ordering and handle oversized page indices without overflow`() {
        val items = (1..5).toList()
        assertEquals(listOf(1, 2), items.libraryPage(0, 2))
        assertEquals(listOf(5), items.libraryPage(2, 2))
        assertTrue(items.libraryPage(Int.MAX_VALUE, Int.MAX_VALUE).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { items.libraryPage(-1, 2) }
        assertThrows(IllegalArgumentException::class.java) { items.libraryPage(0, 0) }
    }

    @Test fun `album browsing serves cached tracks without networking`() = runTest {
        val cached = album(listOf(song(2), song(1)))
        store.saveAlbum(cached)
        catalog.lookup = { throw IOException("offline") }
        assertEquals(cached, library.album(10))
    }

    @Test fun `uncached album is fetched and persisted for subsequent offline browsing`() = runTest {
        val expected = album(listOf(song(7)))
        catalog.lookup = { expected }
        assertEquals(expected, library.album(10))
        catalog.lookup = { throw IOException("offline") }
        assertEquals(expected, library.album(10))
    }

    @Test fun `recent albums are unique and keep listening recency ordering`() = runTest {
        store.recordPlayed(song(1, albumId = 10), 1)
        store.recordPlayed(song(2, albumId = 20), 2)
        store.recordPlayed(song(3, albumId = 10), 3)
        assertEquals(listOf(10L, 20L), library.albums().map { it.albumId })
    }

    @Test fun `car queue uses album order and excludes unavailable previews`() = runTest {
        store.saveAlbum(album(listOf(song(1), song(2).copy(previewUrl = null), song(3))))
        assertEquals(listOf(1L, 3L), library.queueFor(song(3)).map { it.id })
        store.recordPlayed(song(8, albumId = 99), 1)
        assertEquals(listOf(8L, 9L), library.queueFor(song(9, albumId = 99)).map { it.id })
    }
}
