package com.jujidaw.ui.mixer

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jujidaw.ui.theme.JujiDawTheme
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MixerScreenLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun channelStripsOwnViewportAndSecondaryFxStartsCollapsed() {
        setMixerContent()

        val viewport = composeRule.onNodeWithTag("mixer-strip-viewport").fetchSemanticsNode().boundsInRoot
        val channel = composeRule.onNodeWithTag("mixer-channel-0").fetchSemanticsNode().boundsInRoot
        assertTrue(channel.height >= viewport.height * 0.95f)

        val minimumPx = 44f * composeRule.activity.resources.displayMetrics.density
        val mute = composeRule.onAllNodesWithContentDescription("Mute").onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue(mute.width >= minimumPx)
        assertTrue(mute.height >= minimumPx)
        listOf("mixer-perform-toggle", "mixer-automation-toggle").forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag width", bounds.width >= minimumPx)
            assertTrue("$tag height", bounds.height >= minimumPx)
        }

        composeRule.onNodeWithTag("mixer-perform-fx-grid").assertDoesNotExist()
        composeRule.onNodeWithTag("mixer-perform-toggle").performClick()
        composeRule.onNodeWithTag("mixer-perform-fx-grid").assertIsDisplayed()
    }

    @Test
    fun landscapeMixerScrollsToKeepFullStripControlsReachable() {
        setMixerContent(landscape = true, height = 360.dp)

        val channelFx = composeRule.onNodeWithTag("mixer-channel-fx-0", useUnmergedTree = true)
        channelFx.assertIsNotDisplayed()
        composeRule
            .onNodeWithTag("mixer-strip-viewport")
            .performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(0f, 1_000f) }
        composeRule.waitForIdle()
        channelFx.assertIsDisplayed()
    }

    @Test
    fun faderAndPanGesturesMutateMixerStateAndAutomationRemainsAccessible() {
        val viewModel = setMixerContent()
        val initialFader = viewModel.uiState.value.channels[0].faderDb
        composeRule.onNodeWithTag("mixer-fader-0", useUnmergedTree = true).performTouchInput { click() }
        composeRule.runOnIdle {
            assertNotEquals(initialFader, viewModel.uiState.value.channels[0].faderDb)
        }

        val initialPan = viewModel.uiState.value.channels[0].pan
        composeRule.onNodeWithTag("mixer-pan-0", useUnmergedTree = true).performTouchInput {
            swipe(
                start = Offset(center.x, height * 0.55f),
                end = Offset(center.x, height * 0.4f),
                durationMillis = 200,
            )
        }
        composeRule.runOnIdle {
            assertTrue(viewModel.uiState.value.channels[0].pan > initialPan)
        }

        composeRule.onNodeWithTag("mixer-automation-toggle").performClick()
        composeRule.onNodeWithText("Automation").assertIsDisplayed()
    }

    @Test
    fun insertSheetRemainsAccessibleFromChannelStrip() {
        setMixerContent()

        composeRule.onNodeWithTag("mixer-channel-fx-0").performClick()
        composeRule.onNodeWithText("Inserts – Track 1").assertIsDisplayed()
    }

    private fun setMixerContent(
        landscape: Boolean = false,
        height: Dp? = null,
    ): MixerViewModel {
        val viewModel = MixerViewModel()
        val configuration = Configuration(composeRule.activity.resources.configuration).apply {
            if (landscape) orientation = Configuration.ORIENTATION_LANDSCAPE
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                JujiDawTheme {
                    Box(
                        modifier =
                            if (height == null) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier.fillMaxWidth().height(height)
                            },
                    ) {
                        MixerScreen(viewModel = viewModel)
                    }
                }
            }
        }
        return viewModel
    }
}
