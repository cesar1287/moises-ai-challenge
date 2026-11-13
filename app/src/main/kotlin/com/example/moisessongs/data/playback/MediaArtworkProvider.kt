package com.example.moisessongs.data.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.moisessongs.DependenciesOwner
import com.example.moisessongs.domain.largeArtworkUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.FileNotFoundException
import kotlin.time.Duration.Companion.seconds

internal object ArtworkUris {
    private const val AUTHORITY_SUFFIX = ".artwork"
    const val SONG = "song"
    const val ALBUM = "album"
    fun root(context: Context): Uri = Uri.Builder().scheme("content")
        .authority(context.packageName + AUTHORITY_SUFFIX).build()
    fun song(context: Context, id: Long): Uri = root(context).buildUpon().appendPath(SONG).appendPath(id.toString()).build()
    fun album(context: Context, id: Long): Uri = root(context).buildUpon().appendPath(ALBUM).appendPath(id.toString()).build()
}

/** Car hosts require local artwork URIs. Only accepted media controllers receive read grants. */
class MediaArtworkProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/png"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val app = requireNotNull(context).applicationContext
        val parts = uri.pathSegments
        if (mode != "r" || uri.authority != ArtworkUris.root(app).authority ||
            parts.size != 2 || parts[0] !in setOf(ArtworkUris.SONG, ArtworkUris.ALBUM) ||
            (parts[1].toLongOrNull() ?: 0) <= 0 || uri.query != null) {
            throw FileNotFoundException("Unknown artwork")
        }
        // Return immediately. Cache lookup, image loading, decoding and streaming run on a worker.
        return openPipeHelper(uri, "image/png", null, Unit) { output, _, _, _, _ ->
            ParcelFileDescriptor.AutoCloseOutputStream(output).use { stream ->
                try {
                    runBlocking(Dispatchers.IO) {
                        withTimeout(15.seconds) {
                            val repository = (app as DependenciesOwner).dependencies.repository
                            val id = parts[1].toLong()
                            val url = if (parts[0] == ArtworkUris.SONG) repository.getSong(id)?.artworkUrl
                                else repository.observeAlbum(id).first()?.artworkUrl
                                    ?: repository.observeRecentSongs().first().firstOrNull { it.albumId == id }?.artworkUrl
                            if (url != null) {
                                val request = ImageRequest.Builder(app).data(largeArtworkUrl(url))
                                    .size(600).allowHardware(false).build()
                                var result = app.imageLoader.execute(request)
                                if (result !is SuccessResult && largeArtworkUrl(url) != url) {
                                    result = app.imageLoader.execute(request.newBuilder().data(url).build())
                                }
                                (result as? SuccessResult)?.image?.toBitmap()?.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // A missing/offline image leaves the host's placeholder; playback is unaffected.
                }
            }
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
}
