package com.example.moisessongs.data.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.moisessongs.domain.Song
import com.example.moisessongs.domain.library.MediaIds

internal object SessionKeys {
    const val TOGGLE_REPEAT = "toggle_repeat"
    // MetadataExtras car-host protocol; no Car App template runtime is needed for media browsing.
    const val ALBUM_LINK = "androidx.car.app.mediaextensions.KEY_DESCRIPTION_LINK_MEDIA_ID"
    const val HISTORY_FAILED = "history_failed"
    const val ALBUM_ID = "album_id"
    const val PREVIEW_URL = "preview_url"
    const val STORE_URL = "store_url"
    const val CATALOG_DURATION = "catalog_duration"
    const val DISC_NUMBER = "disc_number"
    const val TRACK_NUMBER = "track_number"
    const val GENRE = "genre"
    const val RELEASE_DATE = "release_date"
    const val EXPLICIT = "explicit"
}

internal fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(MediaIds.song(id))
    .setUri(previewUrl)
    .setMediaMetadata(MediaMetadata.Builder()
        .setTitle(title).setArtist(artist).setSubtitle(artist).setAlbumTitle(album).setDescription(album)
        .setArtworkUri(artworkUrl?.toUri())
        .setIsBrowsable(false).setIsPlayable(previewUrl != null)
        .setExtras(Bundle().apply {
            albumId?.let {
                putLong(SessionKeys.ALBUM_ID, it)
                putString(SessionKeys.ALBUM_LINK, MediaIds.album(it))
            }
            putString(SessionKeys.PREVIEW_URL, previewUrl)
            putString(SessionKeys.STORE_URL, storeUrl)
            putLong(SessionKeys.CATALOG_DURATION, durationMs)
            putInt(SessionKeys.DISC_NUMBER, discNumber)
            putInt(SessionKeys.TRACK_NUMBER, trackNumber)
            putString(SessionKeys.GENRE, genre)
            putString(SessionKeys.RELEASE_DATE, releaseDate)
            putBoolean(SessionKeys.EXPLICIT, explicit)
        }).build())
    .build()

internal fun MediaItem.toSong(): Song? {
    val id = MediaIds.songId(mediaId) ?: return null
    val metadata = mediaMetadata
    val extras = metadata.extras ?: Bundle.EMPTY
    return Song(id, metadata.title?.toString().orEmpty(), metadata.artist?.toString().orEmpty(),
        extras.takeIf { it.containsKey(SessionKeys.ALBUM_ID) }?.getLong(SessionKeys.ALBUM_ID),
        metadata.albumTitle?.toString().orEmpty(), metadata.artworkUri?.toString(),
        extras.getString(SessionKeys.PREVIEW_URL), extras.getString(SessionKeys.STORE_URL),
        extras.getLong(SessionKeys.CATALOG_DURATION), extras.getInt(SessionKeys.TRACK_NUMBER),
        extras.getInt(SessionKeys.DISC_NUMBER, 1), extras.getString(SessionKeys.GENRE).orEmpty(),
        extras.getString(SessionKeys.RELEASE_DATE).orEmpty(), extras.getBoolean(SessionKeys.EXPLICIT))
}

internal fun libraryFolder(id: String, title: String, artworkUrl: String? = null, artist: String? = null): MediaItem =
    MediaItem.Builder().setMediaId(id).setMediaMetadata(MediaMetadata.Builder()
        .setTitle(title).setArtist(artist).setArtworkUri(artworkUrl?.toUri())
        .setIsBrowsable(true).setIsPlayable(false).build()).build()
