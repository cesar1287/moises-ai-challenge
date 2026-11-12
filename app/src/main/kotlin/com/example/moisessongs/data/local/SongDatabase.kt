package com.example.moisessongs.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.example.moisessongs.domain.Album
import com.example.moisessongs.domain.Song
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val albumId: Long?,
    val album: String,
    val artworkUrl: String?,
    val previewUrl: String?,
    val storeUrl: String?,
    val durationMs: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val genre: String,
    val releaseDate: String,
    val explicit: Boolean,
) {
    fun toSong() = Song(id, title, artist, albumId, album, artworkUrl, previewUrl, storeUrl,
        durationMs, trackNumber, discNumber, genre, releaseDate, explicit)
}

fun Song.toEntity() = SongEntity(id, title, artist, albumId, album, artworkUrl, previewUrl,
    storeUrl, durationMs, trackNumber, discNumber, genre, releaseDate, explicit)

@Entity(tableName = "searches")
data class SearchEntity(
    @PrimaryKey val query: String,
    val requestedLimit: Int,
    val endReached: Boolean,
    val updatedAt: Long,
)

@Entity(tableName = "search_results", primaryKeys = ["query", "songId"])
data class SearchResultEntity(val query: String, val songId: Long, val position: Int)

@Entity(tableName = "recent_plays")
data class RecentPlayEntity(@PrimaryKey val songId: Long, val playedAt: Long)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val releaseDate: String,
    val genre: String,
    val trackCount: Int,
    val storeUrl: String?,
)

@Entity(tableName = "album_tracks", primaryKeys = ["albumId", "songId"])
data class AlbumTrackEntity(val albumId: Long, val songId: Long, val position: Int)

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissingSong(song: SongEntity)

    @Query("SELECT q.query AS cache_query, q.requestedLimit AS cache_requestedLimit, q.endReached AS cache_endReached, q.updatedAt AS cache_updatedAt, s.* FROM searches q LEFT JOIN search_results r ON q.query = r.query LEFT JOIN songs s ON s.id = r.songId WHERE q.query = :query ORDER BY r.position")
    fun observeSearchRows(query: String): Flow<List<SearchRow>>

    @Query("SELECT a.id AS album_id, a.title AS album_title, a.artist AS album_artist, a.artworkUrl AS album_artworkUrl, a.releaseDate AS album_releaseDate, a.genre AS album_genre, a.trackCount AS album_trackCount, a.storeUrl AS album_storeUrl, s.* FROM albums a LEFT JOIN album_tracks t ON a.id = t.albumId LEFT JOIN songs s ON s.id = t.songId WHERE a.id = :id ORDER BY t.position")
    fun observeAlbumRows(id: Long): Flow<List<AlbumRow>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSongs(songs: List<SongEntity>)

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSong(id: Long): SongEntity?

    @Query("SELECT * FROM searches WHERE query = :query")
    suspend fun getSearch(query: String): SearchEntity?

    @Query("SELECT s.* FROM songs s INNER JOIN search_results r ON s.id = r.songId WHERE r.query = :query ORDER BY r.position")
    suspend fun getSearchSongs(query: String): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSearch(search: SearchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSearchResults(results: List<SearchResultEntity>)

    @Query("DELETE FROM search_results WHERE query = :query")
    suspend fun clearSearchResults(query: String)

    @Query("SELECT s.* FROM songs s INNER JOIN recent_plays r ON s.id = r.songId ORDER BY r.playedAt DESC, r.songId DESC LIMIT 20")
    fun observeRecentSongs(): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRecentPlay(play: RecentPlayEntity)

    @Query("DELETE FROM recent_plays WHERE songId NOT IN (SELECT songId FROM recent_plays ORDER BY playedAt DESC, songId DESC LIMIT 20)")
    suspend fun trimRecentPlays()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAlbum(album: AlbumEntity)

    @Query("DELETE FROM album_tracks WHERE albumId = :id")
    suspend fun clearAlbumTracks(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAlbumTracks(tracks: List<AlbumTrackEntity>)
}

fun Album.toEntity() = AlbumEntity(id, title, artist, artworkUrl, releaseDate, genre, trackCount, storeUrl)

@Database(
    entities = [SongEntity::class, SearchEntity::class, SearchResultEntity::class,
        RecentPlayEntity::class, AlbumEntity::class, AlbumTrackEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SongDatabase : RoomDatabase() {
    abstract fun songs(): SongDao
}

data class SearchRow(@Embedded(prefix = "cache_") val cache: SearchEntity, @Embedded val song: SongEntity?)
data class AlbumRow(@Embedded(prefix = "album_") val album: AlbumEntity, @Embedded val song: SongEntity?)
