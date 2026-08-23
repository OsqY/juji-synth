package com.jujidaw.ui.performance

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jujidaw.ui.keyboard.KeyboardScreen
import com.jujidaw.ui.keyboard.KeyboardViewModel
import com.jujidaw.ui.pads.PadsScreen
import com.jujidaw.ui.pads.PadsViewModel
import com.jujidaw.ui.theme.JujiDawTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformanceScreensLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun padsKeepGridPrimaryAndRevealEditTools() {
        composeRule.setContent {
            JujiDawTheme { PadsScreen(viewModel = PadsViewModel()) }
        }

        composeRule.onNodeWithTag("pads-edit-tools").assertDoesNotExist()
        val root = composeRule.onNodeWithTag("pads-root").fetchSemanticsNode().boundsInRoot
        val grid = composeRule.onNodeWithTag("pads-grid").fetchSemanticsNode().boundsInRoot
        assertSingleToolbarChrome("Pads", root.height, grid.height)
        assertMinimumTouchTarget("pads-bank-a")
        assertMinimumTouchTarget("pads-tools-toggle")
        composeRule.onNodeWithTag("pads-pad-0", useUnmergedTree = true).assertIsSelected()
        composeRule.onNodeWithTag("pads-pad-0", useUnmergedTree = true).assertHasClickAction()

        composeRule.onNodeWithTag("pads-tools-toggle").performClick()
        composeRule.onNodeWithTag("pads-edit-tools").assertIsDisplayed()
        assertMinimumTouchTarget("pads-import")
        assertMinimumTouchTarget("pads-edit")
    }

    @Test
    fun keysKeepAdvancedControlsCollapsedAndGridPrimary() {
        composeRule.setContent {
            JujiDawTheme { KeyboardScreen(viewModel = KeyboardViewModel()) }
        }

        composeRule.onNodeWithTag("keyboard-control-strip").assertDoesNotExist()
        val root = composeRule.onNodeWithTag("keyboard-root").fetchSemanticsNode().boundsInRoot
        val grid = composeRule.onNodeWithTag("keyboard-grid").fetchSemanticsNode().boundsInRoot
        assertSingleToolbarChrome("Keyboard", root.height, grid.height)
        listOf(
            "keyboard-target",
            "keyboard-octave-down",
            "keyboard-octave-up",
            "keyboard-view-grid",
            "keyboard-view-piano",
            "keyboard-controls-toggle",
        ).forEach(::assertMinimumTouchTarget)
        composeRule.onNodeWithTag("keyboard-key-48", useUnmergedTree = true).assertHasClickAction()

        composeRule.onNodeWithTag("keyboard-controls-toggle").performClick()
        composeRule.onNodeWithTag("keyboard-control-strip").assertIsDisplayed()
        assertMinimumTouchTarget("keyboard-velocity-toggle")
    }

    private fun assertMinimumTouchTarget(tag: String) {
        val minimumPx = 44f * composeRule.activity.resources.displayMetrics.density
        val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag width", bounds.width >= minimumPx)
        assertTrue("$tag height", bounds.height >= minimumPx)
    }

    private fun assertSingleToolbarChrome(
        surface: String,
        rootHeight: Float,
        gridHeight: Float,
    ) {
        val maximumChromePx = 60f * composeRule.activity.resources.displayMetrics.density
        val chromeHeight = rootHeight - gridHeight
        assertTrue("$surface chrome was $chromeHeight px", chromeHeight <= maximumChromePx)
    }
}
