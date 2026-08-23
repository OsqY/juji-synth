package com.jujidaw.ui.main

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.jujidaw.ui.theme.JujiDawTheme
import org.junit.Rule
import org.junit.Test

class MainScreenLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun landscapeTimelineStartsWithInlineControlsCollapsed() {
        val landscape = Configuration(composeRule.activity.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides landscape) {
                JujiDawTheme { MainScreen() }
            }
        }

        composeRule.onNodeWithTag("timeline-controls-toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-zoom-indicator").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("Play").assertCountEquals(1)
    }
}
