package com.jujidaw.ui.help

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jujidaw.ui.theme.JujiDawTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelpScreenLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun landscapeContentStaysInsideGuide() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeRule.waitUntil(5_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        composeRule.setContent {
            JujiDawTheme { HelpScreen(onClose = {}) }
        }

        val root = composeRule.onNodeWithTag("workflow-guide-root").fetchSemanticsNode().boundsInRoot
        listOf("Transport", "Timeline: draw sources into rows").forEach { text ->
            val bounds = composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
            assertTrue("$text extends past the guide: $bounds", bounds.right <= root.right)
        }
    }
}
