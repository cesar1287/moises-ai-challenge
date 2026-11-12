package com.example.moisessongs.data

import com.example.moisessongs.*
import com.example.moisessongs.domain.CatalogSearch
import com.example.moisessongs.domain.SearchSnapshot
import com.example.moisessongs.domain.DataFailure
import com.example.moisessongs.domain.DataFailureException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DefaultSongRepositoryTest {
    private val catalog = FakeCatalog()
    private val store = MemorySongStore()
    private val repository = DefaultSongRepository(catalog, store) { 1234L }

    @Test fun `increasing prefixes provide eight pages without duplicates and stop at the cap`() = runTest {
        repeat(10) { repository.loadSearch("Artist") }
        assertEquals((25..200 step 25).toList(), catalog.requests.map { it.second })
        val cached = repository.observeSearch("artist").first()!!
        assertEquals(200, cached.songs.size)
        assertEquals(200, cached.songs.map { it.id }.distinct().size)
        assertTrue(cached.endReached)
        assertEquals(1234L, cached.updatedAt)
    }

    @Test fun `cache identity ignores casing and repeated whitespace`() = runTest {
        repository.loadSearch("  Daft   Punk  ")
        repository.loadSearch("daft punk")
        assertEquals(listOf(25, 50), catalog.requests.map { it.second })
        assertEquals(50, repository.observeSearch("DAFT PUNK").first()!!.songs.size)
    }

    @Test fun `short page and empty results terminate pagination`() = runTest {
        catalog.search = { _, _ -> CatalogSearch(listOf(song()), 1) }
        repository.loadSearch("short")
        repository.loadSearch("short")
        assertEquals(1, catalog.requests.size)
        catalog.search = { _, _ -> CatalogSearch(emptyList(), 0) }
        repository.loadSearch("none")
        val empty = repository.observeSearch("none").first()!!
        assertTrue(empty.songs.isEmpty())
        assertTrue(empty.endReached)
    }

    @Test fun `refresh replaces old results while append deduplicates stable identifiers`() = runTest {
        repository.loadSearch("artist")
        catalog.search = { _, _ -> CatalogSearch(listOf(song(40), song(40), song(1)), 50) }
        repository.loadSearch("artist")
        val appended = repository.observeSearch("artist").first()!!
        assertEquals(26, appended.songs.size)
        assertEquals(listOf(40L, 1L), appended.songs.take(2).map { it.id })
        catalog.search = { _, _ -> CatalogSearch(listOf(song(99)), 1) }
        repository.loadSearch("artist", refresh = true)
        assertEquals(listOf(99L), repository.observeSearch("artist").first()!!.songs.map { it.id })
        assertEquals(50, catalog.requests.last().second)
    }

    @Test fun `failed append and refresh preserve all cached results`() = runTest {
        repository.loadSearch("artist")
        val before = repository.observeSearch("artist").first()
        catalog.search = { _, _ -> throw IOException("offline") }
        for (refresh in listOf(false, true)) {
            try { repository.loadSearch("artist", refresh); fail("Expected network failure") }
            catch (failure: DataFailureException) { assertEquals(DataFailure.CONNECTION, failure.failure) }
            assertEquals(before, repository.observeSearch("artist").first())
        }
    }

    @Test fun `cancellation is propagated without committing partial results`() = runTest {
        catalog.search = { _, _ -> throw CancellationException("new query") }
        try { repository.loadSearch("old"); fail("Expected cancellation") }
        catch (_: CancellationException) { }
        assertNull(repository.observeSearch("old").first())
    }

    @Test fun `blank searches do not call the catalog`() = runTest {
        repository.loadSearch("   ")
        assertTrue(catalog.requests.isEmpty())
    }

    @Test fun `recent plays are unique and most recent first`() = runTest {
        repository.recordPlayed(song(1))
        repository.recordPlayed(song(2))
        repository.recordPlayed(song(1))
        assertEquals(listOf(1L, 2L), repository.observeRecentSongs().first().map { it.id })
    }

    @Test fun `failed album refresh retains the saved album and its tracks`() = runTest {
        repository.refreshAlbum(10)
        val cached = repository.observeAlbum(10).first()
        catalog.lookup = { throw IOException("offline") }
        try { repository.refreshAlbum(10); fail("Expected failure") }
        catch (failure: DataFailureException) { assertEquals(DataFailure.CONNECTION, failure.failure) }
        assertEquals(cached, repository.observeAlbum(10).first())
        assertEquals(song(), repository.getSong(1))
    }

    @Test fun `catalog and search processing execute on the injected worker dispatcher`() = runTest {
        val worker = Executors.newSingleThreadExecutor { Thread(it, "catalog-worker") }.asCoroutineDispatcher()
        try {
            var thread = ""
            catalog.search = { _, _ -> thread = Thread.currentThread().name; CatalogSearch(listOf(song()), 1) }
            DefaultSongRepository(catalog, store, computationDispatcher = worker).loadSearch("Artist")
            assertEquals("catalog-worker", thread)
        } finally { worker.close() }
    }
}
