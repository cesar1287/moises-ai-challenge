package com.example.moisessongs.data

import android.content.Context
import androidx.room.Room
import com.example.moisessongs.data.local.RoomSongStore
import com.example.moisessongs.data.local.SongDatabase
import com.example.moisessongs.data.playback.Media3PreviewPlayer
import com.example.moisessongs.data.remote.ITunesApi
import com.example.moisessongs.data.remote.ITunesCatalog
import com.example.moisessongs.data.remote.ITunesConfig
import com.example.moisessongs.domain.SongRepository
import com.example.moisessongs.domain.playback.PreviewPlayer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** The application's composition root sees contracts, without configuring transport or storage. */
class DataDependencies(context: Context) {
    private val application = context.applicationContext
    private val database by lazy { Room.databaseBuilder(application, SongDatabase::class.java, SongDatabase.NAME).build() }
    // Construct Retrofit/OkHttp on the catalog's worker dispatcher, only when networking is needed.
    private val api by lazy {
        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
        Retrofit.Builder().baseUrl(ITunesConfig.BASE_URL).client(client)
            .addConverterFactory(ITunesConfig.json.asConverterFactory(ITunesConfig.jsonMediaType))
            .build().create(ITunesApi::class.java)
    }
    val repository: SongRepository by lazy {
        DefaultSongRepository(ITunesCatalog({ api }), RoomSongStore(database))
    }
    fun createPlayer(scope: CoroutineScope): PreviewPlayer = Media3PreviewPlayer(application, scope)
}
