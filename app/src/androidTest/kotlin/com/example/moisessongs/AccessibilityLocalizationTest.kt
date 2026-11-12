package com.example.moisessongs

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.example.moisessongs.domain.playback.PlaybackState
import com.example.moisessongs.ui.player.PlayerScreen
import com.example.moisessongs.ui.songs.SongsScreen
import com.example.moisessongs.ui.songs.SongsUiState
import com.example.moisessongs.ui.theme.MoisesSongsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AccessibilityLocalizationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun playerPassesAccessibilityChecksAndSupportsSemanticSeeking() {
        var seek = -1L
        rule.setContent {
            MoisesSongsTheme {
                PlayerScreen(PlaybackState(song = testSong(), positionMs = 5_000, durationMs = 30_000),
                    listOf(testSong()), false, {}, {}, {}, {}, {}, { seek = it }, {}, {})
            }
        }
        rule.enableAccessibilityChecks()
        rule.onRoot().tryPerformAccessibilityChecks()
        val timeline = rule.onNodeWithContentDescription("Preview timeline")
        timeline.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0:05 of 0:30"))
        timeline.assertTouchHeightIsEqualTo(48.dp)
        timeline.performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        rule.runOnIdle { assertEquals(15_000L, seek) }
    }

    @Test fun portuguesePlayerAtDoubleFontSizeAndRtlKeepsControlsReachable() {
        var clicked = false
        rule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 480.dp)) then
                    DeviceConfigurationOverride.FontScale(2f) then
                    DeviceConfigurationOverride.Locales(LocaleList("pt-BR")) then
                    DeviceConfigurationOverride.LayoutDirection(LayoutDirection.Rtl)
            ) {
                MoisesSongsTheme {
                    PlayerScreen(PlaybackState(song = testSong(), positionMs = 5_000, durationMs = 30_000),
                        listOf(testSong()), false, {}, {}, { clicked = true }, {}, {}, {}, {}, {})
                }
            }
        }
        rule.onNodeWithContentDescription("Reproduzir prévia").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(clicked)
        rule.onNodeWithContentDescription("Posição da prévia").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0:05 de 0:30"))
        rule.onNodeWithContentDescription("Voltar").assertIsDisplayed()
    }

    @Test fun portugueseSongsExposeRefreshWithoutAPullGesture() {
        var refreshed = false
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("pt-BR"))) {
                MoisesSongsTheme {
                    SongsScreen(SongsUiState(recentSongs = listOf(testSong())), {}, {}, {},
                        { refreshed = true }, {}, {}, {}, {})
                }
            }
        }
        rule.onNodeWithText("Músicas").assertIsDisplayed()
        val actions = rule.onNodeWithTag("song_list").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnIdle {
            assertTrue(actions.single { it.label == "Atualizar músicas" }.action())
        }
        assertTrue(refreshed)
    }
}
