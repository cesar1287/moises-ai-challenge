package com.example.moisessongs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.moisessongs.data.local.toEntity
import com.example.moisessongs.data.playback.ArtworkUris
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaArtworkProviderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val application get() = context.applicationContext as InstrumentedSongsApplication
    private val resolver get() = context.contentResolver
    private var imageFile: File? = null

    @After fun cleanup() { imageFile?.delete() }

    @Test fun artworkReadsUseTheDeclaredAuthorityAndReturnADecodablePng() {
        val image = File.createTempFile("artwork-test-", ".png", context.cacheDir).also { imageFile = it }
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try { image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        runBlocking {
            application.database.songs().putSongs(listOf(testSong().copy(artworkUrl = Uri.fromFile(image).toString()).toEntity()))
        }
        val uri = ArtworkUris.song(context, 1)
        assertEquals("image/png", resolver.getType(uri))
        val bytes = requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() }
        assertArrayEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), bytes.take(8).toByteArray())
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try { assertEquals(Color.BLUE, decoded.getPixel(decoded.width / 2, decoded.height / 2)) }
        finally { decoded.recycle() }
    }

    @Test fun artworkRejectsInvalidIdsPathsAndQueryParametersBeforeLoading() {
        val root = ArtworkUris.root(context)
        val invalid = listOf(
            root,
            root.buildUpon().appendPath("song").appendPath("0").build(),
            root.buildUpon().appendPath("song").appendPath("-1").build(),
            root.buildUpon().appendPath("song").appendPath("not-an-id").build(),
            root.buildUpon().appendPath("other").appendPath("1").build(),
            ArtworkUris.song(context, 1).buildUpon().appendPath("extra").build(),
            ArtworkUris.song(context, 1).buildUpon().appendQueryParameter("url", "file:///private").build(),
        )
        for (uri in invalid) {
            assertThrows(FileNotFoundException::class.java) { resolver.openFileDescriptor(uri, "r")?.close() }
        }
    }

    @Test fun artworkRejectsWriteAndReadWriteAccess() {
        for (mode in listOf("w", "rw", "wa")) {
            assertThrows(FileNotFoundException::class.java) {
                resolver.openFileDescriptor(ArtworkUris.song(context, 1), mode)?.close()
            }
        }
    }
}
