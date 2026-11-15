package com.example.moisessongs.data

import com.example.moisessongs.data.remote.ITunesApi
import com.example.moisessongs.data.remote.ITunesCatalog
import com.example.moisessongs.data.remote.ITunesConfig
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ITunesCatalogTest {
    private val server = MockWebServer()
    private lateinit var catalog: ITunesCatalog
    @Before fun setup() {
        server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(ITunesConfig.json.asConverterFactory(ITunesConfig.jsonMediaType))
            .build().create(ITunesApi::class.java)
        catalog = ITunesCatalog(api)
    }
    @After fun teardown() = server.shutdown()

    @Test fun `search encodes text and filters incomplete and non-song records`() = runTest {
        server.enqueue(MockResponse().setBody("""{"resultCount":4,"results":[
            {"kind":"song","trackId":1,"trackName":"Song","artistName":"Artist","previewUrl":"http://example.com/audio","unknown":"ignored"},
            {"kind":"song","trackId":2,"trackName":null,"artistName":"Artist"},
            {"wrapperType":"collection","collectionId":3},
            {"kind":"song","trackId":4,"trackName":"Other","artistName":"Artist","previewUrl":"javascript:bad"}
        ]}"""))
        val response = catalog.search("AC/DC & friends", 25)
        assertEquals(4, response.rawResultCount)
        assertEquals(listOf(1L, 4L), response.songs.map { it.id })
        assertEquals("https://example.com/audio", response.songs.first().previewUrl)
        assertNull(response.songs.last().previewUrl)
        assertEquals(0L, response.songs.first().durationMs)
        val request = server.takeRequest().requestUrl!!
        assertEquals("/search", request.encodedPath)
        assertEquals("AC/DC & friends", request.queryParameter("term"))
        assertEquals("music", request.queryParameter("media"))
        assertEquals("US", request.queryParameter("country"))
        assertEquals("song", request.queryParameter("entity"))
        assertEquals("25", request.queryParameter("limit"))
        assertNull(request.queryParameter("offset"))
    }

    @Test fun `album lookup separates header and orders tracks by disc then track`() = runTest {
        server.enqueue(MockResponse().setBody("""{"resultCount":4,"results":[
            {"wrapperType":"collection","collectionId":10,"collectionName":"Album","artistName":"Artist","trackCount":3},
            {"kind":"song","trackId":3,"trackName":"Third","artistName":"Artist","discNumber":2,"trackNumber":1},
            {"kind":"song","trackId":2,"trackName":"Second","artistName":"Artist","discNumber":1,"trackNumber":2},
            {"kind":"song","trackId":1,"trackName":"First","artistName":"Artist","discNumber":1,"trackNumber":1}
        ]}"""))
        val album = catalog.album(10)
        assertEquals("Album", album.title)
        assertEquals(listOf(1L, 2L, 3L), album.songs.map { it.id })
        val request = server.takeRequest().requestUrl!!
        assertEquals("/lookup", request.encodedPath)
        assertEquals("10", request.queryParameter("id"))
        assertEquals("song", request.queryParameter("entity"))
        assertEquals("US", request.queryParameter("country"))
        assertEquals("200", request.queryParameter("limit"))
    }

    @Test fun `album rejects a response without its collection header`() = runTest {
        server.enqueue(MockResponse().setBody("""{"results":[
            {"kind":"song","trackId":1,"trackName":"Song","artistName":"Artist"}
        ]}"""))
        try { catalog.album(10); fail("Expected missing album") }
        catch (_: java.io.IOException) { }
    }

    @Test fun `album deduplicates tracks and maps explicitness durations and secure URLs`() = runTest {
        server.enqueue(MockResponse().setBody("""{"results":[
            {"wrapperType":"collection","collectionName":"Album","artworkUrl100":"http://example.com/art.png","collectionViewUrl":"http://example.com/album"},
            {"kind":"song","trackId":1,"trackName":"First","artistName":"Artist","trackExplicitness":"explicit","trackTimeMillis":-100,"previewUrl":"http://example.com/audio"},
            {"kind":"song","trackId":1,"trackName":"Duplicate","artistName":"Artist"},
            {"kind":"song","trackId":2,"trackName":"Clean","artistName":"Artist","trackExplicitness":"clean","previewUrl":"file:///private/audio"},
            {"kind":"music-video","trackId":3,"trackName":"Video","artistName":"Artist"}
        ]}"""))
        val album = catalog.album(10)
        assertEquals("https://example.com/art.png", album.artworkUrl)
        assertEquals("https://example.com/album", album.storeUrl)
        assertEquals(listOf(1L, 2L), album.songs.map { it.id })
        assertEquals("First", album.songs.first().title)
        assertTrue(album.songs.first().explicit)
        assertEquals(0L, album.songs.first().durationMs)
        assertEquals(1, album.songs.first().discNumber)
        assertEquals("https://example.com/audio", album.songs.first().previewUrl)
        assertFalse(album.songs.last().explicit)
        assertNull(album.songs.last().previewUrl)
    }

    @Test fun `service failures are propagated for the presentation layer to classify`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        try { catalog.search("test", 25); fail("Expected rate limit") }
        catch (failure: HttpException) { assertEquals(429, failure.code()) }
    }
}
