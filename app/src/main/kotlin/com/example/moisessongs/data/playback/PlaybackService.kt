package com.example.moisessongs.data.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.example.moisessongs.DependenciesOwner
import com.example.moisessongs.MainActivity
import com.example.moisessongs.R
import com.example.moisessongs.domain.SongRepository
import com.example.moisessongs.domain.library.MediaIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Owns playback independently of the phone Activity, including while Android Auto is connected. */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: SongRepository
    private var session: MediaLibrarySession? = null
    private var engine: ExoPlayer? = null
    private var recordedId: Long? = null
    private var recordingId: Long? = null

    override fun onCreate() {
        super.onCreate()
        repository = (application as DependenciesOwner).dependencies.repository
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        engine = player
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaLibrarySession.Builder(this, player, SongLibraryCallback(this, repository, scope))
            .setSessionActivity(activity).build()
        updateRepeatButton()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) recordCurrentSong() }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                recordCurrentSong()
            }
            override fun onRepeatModeChanged(repeatMode: Int) { updateRepeatButton() }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    private fun updateRepeatButton() {
        val repeating = engine?.repeatMode == Player.REPEAT_MODE_ONE
        session?.setMediaButtonPreferences(listOf(CommandButton.Builder(
            if (repeating) CommandButton.ICON_REPEAT_ONE else CommandButton.ICON_REPEAT_ALL)
            .setDisplayName(getString(if (repeating) R.string.repeat_on else R.string.repeat_off))
            .setSessionCommand(SessionCommand(SessionKeys.TOGGLE_REPEAT, Bundle.EMPTY)).build()))
    }

    private fun recordCurrentSong() {
        val player = engine ?: return
        val id = MediaIds.songId(player.currentMediaItem?.mediaId.orEmpty()) ?: return
        if (!player.isPlaying || id == recordedId || id == recordingId) return
        recordingId = id
        scope.launch {
            try {
                val song = requireNotNull(repository.getSong(id))
                repository.recordPlayed(song)
                publishLibraryChanges()
                if (MediaIds.songId(player.currentMediaItem?.mediaId.orEmpty()) == id) {
                    recordedId = id
                    session?.setSessionExtras(Bundle().apply { putBoolean(SessionKeys.HISTORY_FAILED, false) })
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (MediaIds.songId(player.currentMediaItem?.mediaId.orEmpty()) == id) {
                    session?.setSessionExtras(Bundle().apply { putBoolean(SessionKeys.HISTORY_FAILED, true) })
                }
            } finally {
                if (recordingId == id) recordingId = null
            }
        }
    }

    private fun publishLibraryChanges() {
        scope.launch {
            val songs = try { repository.observeRecentSongs().first() }
            catch (cancelled: CancellationException) { throw cancelled }
            // Notifications are best-effort; every browser request reads the cache again.
            catch (_: Exception) { return@launch }
            session?.notifyChildrenChanged(MediaIds.RECENT, songs.count { it.previewUrl != null }, null)
            session?.notifyChildrenChanged(MediaIds.ALBUMS, songs.mapNotNull { it.albumId }.distinct().size, null)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        engine?.release()
        engine = null
        super.onDestroy()
    }
}
