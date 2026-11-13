package com.example.moisessongs

import android.os.Bundle
import android.os.Build
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.moisessongs.ui.player.PlayerViewModel
import com.example.moisessongs.ui.common.SavedStateKeys
import com.example.moisessongs.ui.theme.MoisesSongsTheme

class MainActivity : ComponentActivity() {
    private var showPlayer by mutableStateOf(false)
    private fun provideDependencies(): AppDependencies = (application as DependenciesOwner).dependencies
    private val player: PlayerViewModel by viewModels {
        viewModelFactory {
            initializer {
                val dependencies = provideDependencies()
                PlayerViewModel(dependencies.repository, createSavedStateHandle()) { scope ->
                    dependencies.createPlayer(scope)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        showPlayer = savedInstanceState?.getBoolean(SavedStateKeys.PENDING_PLAYER_REQUEST)
            ?: (intent.action == NavigationActions.SHOW_PLAYER)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        setContent {
            MoisesSongsTheme {
                MainNavigation(provideDependencies().repository, player, openPlayer = showPlayer,
                    onPlayerOpened = { showPlayer = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showPlayer = intent.action == NavigationActions.SHOW_PLAYER
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // A consumed notification must not reopen the player after rotation or process recreation.
        outState.putBoolean(SavedStateKeys.PENDING_PLAYER_REQUEST, showPlayer)
        super.onSaveInstanceState(outState)
    }
}
