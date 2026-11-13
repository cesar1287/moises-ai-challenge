package com.example.moisessongs

import android.net.Uri
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.moisessongs.data.playback.Media3PreviewPlayer
import com.example.moisessongs.data.playback.PlaybackService
import com.example.moisessongs.data.local.toEntity
import com.example.moisessongs.domain.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class Media3PreviewPlayerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: Media3PreviewPlayer
    private lateinit var activity: ActivityScenario<ComponentActivity>
    private lateinit var source: File
    private val application get() = instrumentation.targetContext.applicationContext as InstrumentedSongsApplication

    @Before fun setup() {
        application.database.clearAllTables()
        activity = ActivityScenario.launch(ComponentActivity::class.java)
        source = File(instrumentation.targetContext.cacheDir, "test_tone.wav")
        instrumentation.context.assets.open("test_tone.wav").use { input ->
            source.outputStream().use(input::copyTo)
        }
        instrumentation.runOnMainSync { player = Media3PreviewPlayer(instrumentation.targetContext, scope) }
    }

    @After fun cleanup() {
        instrumentation.runOnMainSync { player.release() }
        scope.cancel()
        instrumentation.targetContext.stopService(Intent(instrumentation.targetContext, PlaybackService::class.java))
        instrumentation.waitForIdleSync()
        activity.close()
        source.delete()
    }

    private fun prepare(song: Song, autoPlay: Boolean) = runBlocking {
        application.database.songs().putSongs(listOf(song.toEntity()))
        instrumentation.runOnMainSync { player.prepare(song, autoPlay) }
    }

    @Test fun playableDurationSeekingAndPausingUseTheActualPreview() = runBlocking {
        prepare(testSong().copy(previewUrl = Uri.fromFile(source).toString()), true)
        val started = withTimeout(15.seconds) { player.state.first { it.playing && it.durationMs > 0 } }
        assertEquals(2000L, started.durationMs)
        assertNotEquals(testSong().durationMs, started.durationMs)
        instrumentation.runOnMainSync { player.pause(); player.seekTo(1500) }
        val paused = withTimeout(5.seconds) { player.state.first { !it.playing && !it.buffering && it.positionMs >= 1400 } }
        assertFalse(paused.buffering)
        instrumentation.runOnMainSync { player.toggleRepeat() }
        assertTrue(player.state.value.repeat)
    }

    @Test fun completionStopsPlaybackAndToggleRestartsThePreview() = runBlocking {
        prepare(testSong().copy(previewUrl = Uri.fromFile(source).toString()), true)
        withTimeout(15.seconds) { player.state.first { it.ended } }
        assertFalse(player.state.value.playing)
        instrumentation.runOnMainSync { player.togglePlayback() }
        withTimeout(5.seconds) { player.state.first { it.playing && !it.ended } }
        assertTrue(player.state.value.positionMs < 1000)
    }

    @Test fun playbackFailureIsVisibleAndSelectingAnotherSongRecovers() = runBlocking {
        prepare(testSong().copy(previewUrl = Uri.fromFile(File(source.parentFile, "missing.wav")).toString()), true)
        withTimeout(15.seconds) { player.state.first { it.failed } }
        assertFalse(player.state.value.playing)
        prepare(testSong(2).copy(previewUrl = Uri.fromFile(source).toString()), true)
        withTimeout(5.seconds) { player.state.first { it.playing } }
        assertFalse(player.state.value.failed)
        assertEquals(2L, player.state.value.song!!.id)
    }

    @Test fun playResumesWithoutResettingPositionAndIsIdempotent() = runBlocking<Unit> {
        prepare(testSong().copy(previewUrl = Uri.fromFile(source).toString()), false)
        instrumentation.runOnMainSync {
            player.play()
            player.play()
        }
        withTimeout(15.seconds) { player.state.first { it.playing && it.durationMs > 0 } }
        instrumentation.runOnMainSync { player.pause(); player.seekTo(1_000) }
        withTimeout(5.seconds) { player.state.first { !it.playing && it.positionMs >= 900 } }
        instrumentation.runOnMainSync { player.play(); player.play() }
        withTimeout(5.seconds) { player.state.first { it.playing && it.positionMs >= 900 } }
    }

    @Test fun playRetriesTheSamePreviewAfterAFailure() = runBlocking<Unit> {
        val recoveredSource = File(source.parentFile, "recovered-preview-${System.nanoTime()}.wav")
        try {
            prepare(testSong().copy(previewUrl = Uri.fromFile(recoveredSource).toString()), true)
            withTimeout(15.seconds) { player.state.first { it.failed } }
            source.copyTo(recoveredSource, overwrite = true)
            instrumentation.runOnMainSync { player.play() }
            withTimeout(5.seconds) { player.state.first { it.playing && !it.failed } }
        } finally { recoveredSource.delete() }
    }
}
