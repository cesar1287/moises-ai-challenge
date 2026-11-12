package com.example.moisessongs

import android.app.Application
import com.example.moisessongs.data.DataDependencies
import com.example.moisessongs.domain.SongRepository
import com.example.moisessongs.domain.playback.PreviewPlayer
import kotlinx.coroutines.CoroutineScope

interface AppDependencies {
    val repository: SongRepository
    fun createPlayer(scope: CoroutineScope): PreviewPlayer
}

interface DependenciesOwner { val dependencies: AppDependencies }

class SongsApplication : Application(), DependenciesOwner {
    override val dependencies: AppDependencies by lazy { AppContainer(this) }
}

class AppContainer(application: Application) : AppDependencies {
    private val data = DataDependencies(application)
    override val repository: SongRepository get() = data.repository
    override fun createPlayer(scope: CoroutineScope) = data.createPlayer(scope)
}
