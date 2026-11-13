package com.example.moisessongs

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.runner.AndroidJUnitRunner
import com.example.moisessongs.data.DefaultSongRepository
import com.example.moisessongs.data.local.RoomSongStore
import com.example.moisessongs.data.local.SongDatabase
import com.example.moisessongs.data.playback.Media3PreviewPlayer
import com.example.moisessongs.domain.Album
import com.example.moisessongs.domain.CatalogSearch
import com.example.moisessongs.domain.SongCatalog
import kotlinx.coroutines.CoroutineScope
import java.io.IOException

class SongsTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, InstrumentedSongsApplication::class.java.name, context)
}

/** Service integration tests use real Room and Media3 with a deterministic, offline catalog. */
class InstrumentedSongsApplication : Application(), DependenciesOwner {
    val database by lazy { Room.inMemoryDatabaseBuilder(this, SongDatabase::class.java).build() }
    val catalog = InstrumentedCatalog()
    override val dependencies: AppDependencies by lazy {
        object : AppDependencies {
            override val repository = DefaultSongRepository(catalog, RoomSongStore(database))
            override fun createPlayer(scope: CoroutineScope) = Media3PreviewPlayer(this@InstrumentedSongsApplication, scope)
        }
    }
}

class InstrumentedCatalog : SongCatalog {
    var search: suspend (String, Int) -> CatalogSearch = { _, _ -> throw IOException("offline test catalog") }
    var lookup: suspend (Long) -> Album = { throw IOException("offline test catalog") }
    override suspend fun search(query: String, limit: Int) = search.invoke(query, limit)
    override suspend fun album(id: Long) = lookup(id)
}
