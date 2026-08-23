package com.jujidaw.ui.synth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jujidaw.ui.theme.JujiDawTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SynthScreenLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun deviceSectionsAreFlatAndTopActionsMeetTouchTargets() {
        setSynthContent()

        composeRule.onNodeWithTag("synth-target-picker").assertDoesNotExist()
        listOf("OSCILLATORS", "FILTER", "ENVELOPES", "LFO", "EFFECTS", "MODULATION").forEach { title ->
            composeRule.onAllNodesWithText(title).assertCountEquals(1)
        }

        val minimumPx = 44f * composeRule.activity.resources.displayMetrics.density
        listOf("synth-midi", "synth-presets", "synth-save", "synth-panic").forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag width", bounds.width >= minimumPx)
            assertTrue("$tag height", bounds.height >= minimumPx)
        }
    }

    @Test
    fun targetPickerIsRevealableAndKeepsLargeControls() {
        setSynthContent()

        composeRule.onNodeWithTag("synth-target-toggle").performClick()
        composeRule.onNodeWithTag("synth-target-picker").assertIsDisplayed()

        val minimumPx = 44f * composeRule.activity.resources.displayMetrics.density
        listOf("synth-track-0", "synth-source-global", "synth-source-pad-0").forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag width", bounds.width >= minimumPx)
            assertTrue("$tag height", bounds.height >= minimumPx)
        }
    }

    private fun setSynthContent() {
        composeRule.setContent {
            JujiDawTheme { SynthScreen(viewModel = SynthViewModel()) }
        }
    }
}
