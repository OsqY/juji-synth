package com.jujidaw.ui.sequencer

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jujidaw.ui.theme.JujiDawTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SequencerScreenLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pianoGridUsesAvailableEditorHeight() {
        composeRule.setContent {
            JujiDawTheme { SequencerScreen() }
        }

        composeRule.onNodeWithTag("sequencer-view-piano").performClick()
        composeRule.waitForIdle()

        val height = composeRule.onNodeWithTag("sequencer-piano-grid").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Piano grid height was $height px", height > 100f)
    }
}
