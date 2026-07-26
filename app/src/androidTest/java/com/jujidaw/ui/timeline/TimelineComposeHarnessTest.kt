package com.jujidaw.ui.timeline

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jujidaw.ui.theme.JujiDawTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Minimal deterministic surface contract for the real Timeline composable.
 * Interaction scenarios are added in the following instrumentation modules.
 */
@RunWith(AndroidJUnit4::class)
class TimelineComposeHarnessTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var timelineViewModel: TimelineViewModel

    @Before
    fun setTimelineContent() {
        timelineViewModel = TimelineViewModel()
        composeRule.setContent {
            JujiDawTheme {
                TimelineScreen(viewModel = timelineViewModel, showTransportControls = true)
            }
        }
    }

    @Test
    fun timelineSurfacesRemainDiscoverableDuringInitialMeasurement() {
        composeRule.onNodeWithTag("timeline-viewport").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-grid").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-ruler").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-playhead").assertIsDisplayed()
    }

    @Test
    fun editingControlsExposeStableTags() {
        composeRule.onNodeWithTag("timeline-pad-selector").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-zoom-indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-snap-indicator").assertIsDisplayed()
    }

    @Test
    fun fiveHundredPercentZoomKeepsViewportAndPlayheadVisible() {
        composeRule.runOnIdle { timelineViewModel.setZoom(5f) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("timeline-viewport").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-grid").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-ruler").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-playhead").assertIsDisplayed()
    }

    @Test
    fun pinchZoomAndHorizontalScrollDoNotEmptyTheViewport() {
        val viewport = composeRule.onNodeWithTag("timeline-viewport")
        composeRule.onNodeWithTag("timeline-playhead").assertIsDisplayed()
        viewport.performTouchInput {
            pinch(
                start0 = center - Offset(40f, 0f),
                end0 = center - Offset(140f, 0f),
                start1 = center + Offset(40f, 0f),
                end1 = center + Offset(140f, 0f),
                durationMillis = 350L,
            )
        }
        viewport.performTouchInput {
            swipeLeft(startX = width * 0.85f, endX = width * 0.15f, durationMillis = 350L)
        }

        composeRule.onNodeWithTag("timeline-grid").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-ruler").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-playhead").assertExists("playhead remains composed when scrolled offscreen")
    }

    @Test
    fun playheadRemainsVisibleAfterSeekingAndZooming() {
        composeRule.runOnIdle {
            timelineViewModel.seekToTick(120L)
            timelineViewModel.setZoom(5f)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-playhead").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-viewport").assertIsDisplayed()
    }

    @Test
    fun deleteToolRemovesShortClipAndRestoresItFromTrash() {
        var clipId: String? = null
        composeRule.runOnIdle {
            val existingIds = timelineViewModel.arrangement.value.clips.mapTo(hashSetOf()) { it.id }
            timelineViewModel.addPadClip(trackIndex = 0, startTick = 0L, padIndex = 0)
            clipId = timelineViewModel.arrangement.value.clips.first { it.id !in existingIds }.id
            timelineViewModel.setTool(TimelineTool.DELETE)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("timeline-delete-tool").assertIsDisplayed().performTouchInput {
            // The clip is only one snap cell wide; the delete hit slop makes
            // it reachable even when the visible body is narrower than a tap.
            click(Offset(2f, 28f))
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val deletedId = requireNotNull(clipId)
            assertTrue(timelineViewModel.arrangement.value.clips.none { it.id == deletedId })
            assertTrue(timelineViewModel.deletedClips.value.any { it.id == deletedId })
            timelineViewModel.restoreLastDeletedClip()
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val restoredId = requireNotNull(clipId)
            assertEquals(restoredId, timelineViewModel.arrangement.value.clips.single { it.id == restoredId }.id)
            assertTrue(timelineViewModel.deletedClips.value.none { it.id == restoredId })
        }
    }
}
