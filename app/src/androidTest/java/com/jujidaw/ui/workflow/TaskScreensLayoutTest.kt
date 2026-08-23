package com.jujidaw.ui.workflow

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jujidaw.ui.SettingsScreen
import com.jujidaw.ui.help.HelpScreen
import com.jujidaw.ui.project.ProjectScreen
import com.jujidaw.ui.sequencer.SequencerScreen
import com.jujidaw.ui.theme.JujiDawTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskScreensLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sequencerKeepsEditorPrimaryAndToolsRevealable() {
        composeRule.setContent { JujiDawTheme { SequencerScreen() } }

        composeRule.onNodeWithTag("sequencer-tools").assertDoesNotExist()
        composeRule.onNodeWithText("▶").assertDoesNotExist()
        assertPrimarySurface("Sequencer", "sequencer-root", "sequencer-editor")
        assertMinimumTouchTarget("sequencer-toolbar")
        assertMinimumTouchTarget("sequencer-tools-toggle")
        composeRule.onNodeWithTag("sequencer-view-step").assertIsSelected()

        composeRule.onNodeWithTag("sequencer-tools-toggle").performClick()
        composeRule.onNodeWithTag("sequencer-tools-toggle").assertIsSelected()
        composeRule.onNodeWithTag("sequencer-tools").assertIsDisplayed()
        assertMinimumTouchTarget("sequencer-copy")
        assertMinimumTouchTarget("sequencer-clear")
    }

    @Test
    fun projectKeepsBrowserPrimaryAndSecondaryActionsRevealable() {
        composeRule.setContent { JujiDawTheme { ProjectScreen() } }

        composeRule.onNodeWithTag("project-tools").assertDoesNotExist()
        composeRule.onNodeWithText("Play").assertDoesNotExist()
        assertPrimarySurface("Project", "project-root", "project-browser")
        assertMinimumTouchTarget("project-new")
        assertMinimumTouchTarget("project-tools-toggle")

        composeRule.onNodeWithTag("project-tools-toggle").performClick()
        composeRule.onNodeWithTag("project-tools-toggle").assertIsSelected()
        composeRule.onNodeWithTag("project-tools").assertIsDisplayed()
        assertMinimumTouchTarget("project-record")
        assertMinimumTouchTarget("project-diagnostics")
        composeRule.onNodeWithContentDescription("Previous recording track").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Next recording track").assertIsDisplayed()
    }

    @Test
    fun settingsOptionsAndCloseMeetTouchTargets() {
        composeRule.setContent { JujiDawTheme { SettingsScreen(onDismiss = {}) } }

        assertMinimumTouchTarget("settings-close")
        assertMinimumTouchTarget("settings-rate-44100")
        assertMinimumTouchTarget("settings-buffer-256")
        composeRule.onNodeWithTag("settings-output-stereo").assertIsSelected()
    }

    @Test
    fun workflowGuideCloseMeetsTouchTarget() {
        composeRule.setContent { JujiDawTheme { HelpScreen(onClose = {}) } }

        assertMinimumTouchTarget("workflow-guide-close")
    }

    private fun assertMinimumTouchTarget(tag: String) {
        val minimumPx = 44f * composeRule.activity.resources.displayMetrics.density
        val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag width", bounds.width >= minimumPx)
        assertTrue("$tag height", bounds.height >= minimumPx)
    }

    private fun assertPrimarySurface(
        name: String,
        rootTag: String,
        surfaceTag: String,
    ) {
        val root = composeRule.onNodeWithTag(rootTag).fetchSemanticsNode().boundsInRoot
        val surface = composeRule.onNodeWithTag(surfaceTag).fetchSemanticsNode().boundsInRoot
        assertTrue("$name surface used ${surface.height}/${root.height}px", surface.height > root.height * 0.7f)
    }
}
