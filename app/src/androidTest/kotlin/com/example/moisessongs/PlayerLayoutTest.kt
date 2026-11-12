package com.example.moisessongs

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.moisessongs.domain.playback.PlaybackState
import com.example.moisessongs.ui.player.PlayerScreen
import com.example.moisessongs.ui.theme.MoisesSongsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PlayerLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun playerControlsRemainReachableOnAShortPhoneWithLargeText() {
        var clicked = false
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 480.dp)) then
                DeviceConfigurationOverride.FontScale(1.5f)) {
                MoisesSongsTheme {
                    PlayerScreen(PlaybackState(song = testSong().copy(title = "A very long song title that wraps across multiple lines"),
                        durationMs = 30_000), listOf(testSong()), false, {}, {}, { clicked = true }, {}, {}, {}, {}, {})
                }
            }
        }
        rule.onNodeWithContentDescription("Play preview").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(clicked)
        rule.onNodeWithContentDescription("Preview timeline").performScrollTo().assertIsDisplayed()
    }
}
