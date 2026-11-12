package com.example.moisessongs

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.moisessongs.data.playback.Media3PreviewPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Media3PreviewPlayerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: Media3PreviewPlayer
    private lateinit var activity: ActivityScenario<ComponentActivity>
    private lateinit var source: File

    @Before fun setup() {
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
        activity.close()
        source.delete()
    }

    @Test fun playableDurationSeekingAndPausingUseTheActualPreview() = runBlocking {
        instrumentation.runOnMainSync {
            player.prepare(testSong().copy(previewUrl = Uri.fromFile(source).toString()), true)
        }
        val started = withTimeout(15_000) { player.state.first { it.playing && it.durationMs > 0 } }
        assertEquals(2000L, started.durationMs)
        assertNotEquals(testSong().durationMs, started.durationMs)
        instrumentation.runOnMainSync { player.pause(); player.seekTo(1500) }
        val paused = withTimeout(5000) { player.state.first { !it.playing && !it.buffering && it.positionMs >= 1400 } }
        assertFalse(paused.buffering)
        instrumentation.runOnMainSync { player.toggleRepeat() }
        assertTrue(player.state.value.repeat)
    }

    @Test fun completionStopsPlaybackAndToggleRestartsThePreview() = runBlocking {
        instrumentation.runOnMainSync {
            player.prepare(testSong().copy(previewUrl = Uri.fromFile(source).toString()), true)
        }
        withTimeout(15_000) { player.state.first { it.ended } }
        assertFalse(player.state.value.playing)
        instrumentation.runOnMainSync { player.togglePlayback() }
        withTimeout(5000) { player.state.first { it.playing && !it.ended } }
        assertTrue(player.state.value.positionMs < 1000)
    }

    @Test fun playbackFailureIsVisibleAndSelectingAnotherSongRecovers() = runBlocking {
        instrumentation.runOnMainSync {
            player.prepare(testSong().copy(previewUrl = Uri.fromFile(File(source.parentFile, "missing.wav")).toString()), true)
        }
        withTimeout(15_000) { player.state.first { it.failed } }
        assertFalse(player.state.value.playing)
        instrumentation.runOnMainSync {
            player.prepare(testSong(2).copy(previewUrl = Uri.fromFile(source).toString()), true)
        }
        withTimeout(5000) { player.state.first { it.playing } }
        assertFalse(player.state.value.failed)
        assertEquals(2L, player.state.value.song!!.id)
    }
}
