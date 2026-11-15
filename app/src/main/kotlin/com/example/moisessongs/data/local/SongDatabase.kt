package com.example.moisessongs.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.example.moisessongs.domain.Album
import com.example.moisessongs.domain.Song
import kotlinx.coroutines.flow.Flow

@Entity(tableName = DatabaseContract.Tables.SONGS)
data class SongEntity(
    @PrimaryKey @ColumnInfo(name = DatabaseContract.Columns.ID) val id: Long,
    @ColumnInfo(name = DatabaseContract.Columns.TITLE) val title: String,
    @ColumnInfo(name = DatabaseContract.Columns.ARTIST) val artist: String,
    @ColumnInfo(name = DatabaseContract.Columns.ALBUM_ID) val albumId: Long?,
    @ColumnInfo(name = DatabaseContract.Columns.ALBUM) val album: String,
    @ColumnInfo(name = DatabaseContract.Columns.ARTWORK_URL) val artworkUrl: String?,
    @ColumnInfo(name = DatabaseContract.Columns.PREVIEW_URL) val previewUrl: String?,
    @ColumnInfo(name = DatabaseContract.Columns.STORE_URL) val storeUrl: String?,
    @ColumnInfo(name = DatabaseContract.Columns.DURATION_MS) val durationMs: Long,
    @ColumnInfo(name = DatabaseContract.Columns.TRACK_NUMBER) val trackNumber: Int,
    @ColumnInfo(name = DatabaseContract.Columns.DISC_NUMBER) val discNumber: Int,
    @ColumnInfo(name = DatabaseContract.Columns.GENRE) val genre: String,
    @ColumnInfo(name = DatabaseContract.Columns.RELEASE_DATE) val releaseDate: String,
    @ColumnInfo(name = DatabaseContract.Columns.EXPLICIT) val explicit: Boolean,
) {
    fun toSong() = Song(id, title, artist, albumId, album, artworkUrl, previewUrl, storeUrl,
        durationMs, trackNumber, discNumber, genre, releaseDate, explicit)
}

fun Song.toEntity() = SongEntity(id, title, artist, albumId, album, artworkUrl, previewUrl,
    storeUrl, durationMs, trackNumber, discNumber, genre, releaseDate, explicit)

@Entity(tableName = DatabaseContract.Tables.SEARCHES)
data class SearchEntity(
    @PrimaryKey @ColumnInfo(name = DatabaseContract.Columns.QUERY) val query: String,
    @ColumnInfo(name = DatabaseContract.Columns.REQUESTED_LIMIT) val requestedLimit: Int,
    @ColumnInfo(name = DatabaseContract.Columns.END_REACHED) val endReached: Boolean,
    @ColumnInfo(name = DatabaseContract.Columns.UPDATED_AT) val updatedAt: Long,
)

@Entity(tableName = DatabaseContract.Tables.SEARCH_RESULTS,
    primaryKeys = [DatabaseContract.Columns.QUERY, DatabaseContract.Columns.SONG_ID])
data class SearchResultEntity(
    @ColumnInfo(name = DatabaseContract.Columns.QUERY) val query: String,
    @ColumnInfo(name = DatabaseContract.Columns.SONG_ID) val songId: Long,
    @ColumnInfo(name = DatabaseContract.Columns.POSITION) val position: Int,
)

@Entity(tableName = DatabaseContract.Tables.RECENT_PLAYS)
data class RecentPlayEntity(
    @PrimaryKey @ColumnInfo(name = DatabaseContract.Columns.SONG_ID) val songId: Long,
    @ColumnInfo(name = DatabaseContract.Columns.PLAYED_AT) val playedAt: Long,
)

@Entity(tableName = DatabaseContract.Tables.ALBUMS)
data class AlbumEntity(
    @PrimaryKey @ColumnInfo(name = DatabaseContract.Columns.ID) val id: Long,
    @ColumnInfo(name = DatabaseContract.Columns.TITLE) val title: String,
    @ColumnInfo(name = DatabaseContract.Columns.ARTIST) val artist: String,
    @ColumnInfo(name = DatabaseContract.Columns.ARTWORK_URL) val artworkUrl: String?,
    @ColumnInfo(name = DatabaseContract.Columns.RELEASE_DATE) val releaseDate: String,
    @ColumnInfo(name = DatabaseContract.Columns.GENRE) val genre: String,
    @ColumnInfo(name = DatabaseContract.Columns.TRACK_COUNT) val trackCount: Int,
    @ColumnInfo(name = DatabaseContract.Columns.STORE_URL) val storeUrl: String?,
)

@Entity(tableName = DatabaseContract.Tables.ALBUM_TRACKS,
    primaryKeys = [DatabaseContract.Columns.ALBUM_ID, DatabaseContract.Columns.SONG_ID])
data class AlbumTrackEntity(
    @ColumnInfo(name = DatabaseContract.Columns.ALBUM_ID) val albumId: Long,
    @ColumnInfo(name = DatabaseContract.Columns.SONG_ID) val songId: Long,
    @ColumnInfo(name = DatabaseContract.Columns.POSITION) val position: Int,
)

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissingSong(song: SongEntity)

    @Query(SongQueries.OBSERVE_SEARCH)
    fun observeSearchRows(query: String): Flow<List<SearchRow>>

    @Query(SongQueries.OBSERVE_ALBUM)
    fun observeAlbumRows(id: Long): Flow<List<AlbumRow>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSongs(songs: List<SongEntity>)

    @Query(SongQueries.GET_SONG)
    suspend fun getSong(id: Long): SongEntity?

    @Query(SongQueries.GET_SEARCH)
    suspend fun getSearch(query: String): SearchEntity?

    @Query(SongQueries.GET_SEARCH_SONGS)
    suspend fun getSearchSongs(query: String): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSearch(search: SearchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSearchResults(results: List<SearchResultEntity>)

    @Query(SongQueries.CLEAR_SEARCH_RESULTS)
    suspend fun clearSearchResults(query: String)

    @Query(SongQueries.OBSERVE_RECENT)
    fun observeRecentSongs(): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRecentPlay(play: RecentPlayEntity)

    @Query(SongQueries.TRIM_RECENT)
    suspend fun trimRecentPlays()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAlbum(album: AlbumEntity)

    @Query(SongQueries.CLEAR_ALBUM_TRACKS)
    suspend fun clearAlbumTracks(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAlbumTracks(tracks: List<AlbumTrackEntity>)
}

fun Album.toEntity() = AlbumEntity(id, title, artist, artworkUrl, releaseDate, genre, trackCount, storeUrl)

@Database(
    entities = [SongEntity::class, SearchEntity::class, SearchResultEntity::class,
        RecentPlayEntity::class, AlbumEntity::class, AlbumTrackEntity::class],
    version = DatabaseContract.VERSION,
    exportSchema = true,
)
abstract class SongDatabase : RoomDatabase() {
    abstract fun songs(): SongDao
}

data class SearchRow(@Embedded(prefix = DatabaseContract.Prefixes.SEARCH) val cache: SearchEntity, @Embedded val song: SongEntity?)
data class AlbumRow(@Embedded(prefix = DatabaseContract.Prefixes.ALBUM) val album: AlbumEntity, @Embedded val song: SongEntity?)
