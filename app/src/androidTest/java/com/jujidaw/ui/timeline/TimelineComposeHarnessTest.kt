package com.jujidaw.ui.timeline

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jujidaw.project.PadSelectionStore
import com.jujidaw.ui.theme.JujiDawTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun capturedMultiClipMoveKeepsOffsetsAndUndoesAsOneTransaction() {
        lateinit var firstId: String
        lateinit var secondId: String
        composeRule.runOnIdle {
            val existingIds = timelineViewModel.arrangement.value.clips.mapTo(hashSetOf()) { it.id }
            timelineViewModel.addPadClip(trackIndex = 0, startTick = 0L, padIndex = 0)
            firstId = timelineViewModel.arrangement.value.clips.first { it.id !in existingIds }.id

            val idsAfterFirst = timelineViewModel.arrangement.value.clips.mapTo(hashSetOf()) { it.id }
            timelineViewModel.addPadClip(trackIndex = 1, startTick = 120L, padIndex = 1)
            secondId = timelineViewModel.arrangement.value.clips.first { it.id !in idsAfterFirst }.id

            timelineViewModel.selectClip(firstId)
            timelineViewModel.selectClip(secondId, addToSelection = true)
            timelineViewModel.moveClips(
                clipIds = setOf(firstId, secondId),
                anchorClipId = firstId,
                newStartTick = 240L,
                newTrackIndex = 2,
            )

            val moved = timelineViewModel.arrangement.value.clips.associateBy { it.id }
            assertEquals(240L, moved.getValue(firstId).startTick)
            assertEquals(2, moved.getValue(firstId).trackIndex)
            assertEquals(360L, moved.getValue(secondId).startTick)
            assertEquals(3, moved.getValue(secondId).trackIndex)
            assertEquals(setOf(firstId, secondId), timelineViewModel.selectedClipIds.value)

            timelineViewModel.undo()
            val restored = timelineViewModel.arrangement.value.clips.associateBy { it.id }
            assertEquals(0L, restored.getValue(firstId).startTick)
            assertEquals(0, restored.getValue(firstId).trackIndex)
            assertEquals(120L, restored.getValue(secondId).startTick)
            assertEquals(1, restored.getValue(secondId).trackIndex)

            timelineViewModel.redo()
            val redone = timelineViewModel.arrangement.value.clips.associateBy { it.id }
            assertEquals(240L, redone.getValue(firstId).startTick)
            assertEquals(360L, redone.getValue(secondId).startTick)
        }
    }

    @Test
    fun extremeTrackTargetsClampBeforeMoveArithmetic() {
        lateinit var clipId: String
        composeRule.runOnIdle {
            val existingIds = timelineViewModel.arrangement.value.clips.mapTo(hashSetOf()) { it.id }
            timelineViewModel.addPadClip(trackIndex = 8, startTick = 0L, padIndex = 0)
            clipId = timelineViewModel.arrangement.value.clips.first { it.id !in existingIds }.id

            timelineViewModel.moveClips(setOf(clipId), clipId, 0L, Int.MIN_VALUE)
            assertEquals(0, timelineViewModel.arrangement.value.clips.single { it.id == clipId }.trackIndex)

            timelineViewModel.moveClips(setOf(clipId), clipId, 0L, Int.MAX_VALUE)
            assertEquals(15, timelineViewModel.arrangement.value.clips.single { it.id == clipId }.trackIndex)
        }
    }

    @Test
    fun draggingSelectedClipMovesTheCapturedGroupAndUndoRestoresIt() {
        lateinit var firstId: String
        lateinit var secondId: String
        composeRule.runOnIdle {
            val existingIds = timelineViewModel.arrangement.value.clips.mapTo(hashSetOf()) { it.id }
            timelineViewModel.addPadClip(trackIndex = 0, startTick = 0L, padIndex = 0)
            firstId = timelineViewModel.arrangement.value.clips.first { it.id !in existingIds }.id

            val idsAfterFirst = timelineViewModel.arrangement.value.clips.mapTo(hashSetOf()) { it.id }
            timelineViewModel.addPadClip(trackIndex = 1, startTick = 120L, padIndex = 1)
            secondId = timelineViewModel.arrangement.value.clips.first { it.id !in idsAfterFirst }.id

            timelineViewModel.selectClip(firstId)
            timelineViewModel.selectClip(secondId, addToSelection = true)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("timeline-clip-$firstId").performTouchInput {
            down(center)
            moveTo(center + Offset(240f, 0f), delayMillis = 300L)
            up()
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val moved = timelineViewModel.arrangement.value.clips.associateBy { it.id }
            val movedFirstStart = moved.getValue(firstId).startTick
            assertTrue(movedFirstStart > 0L)
            assertEquals(movedFirstStart + 120L, moved.getValue(secondId).startTick)
            assertEquals(0, moved.getValue(firstId).trackIndex)
            assertEquals(1, moved.getValue(secondId).trackIndex)
            assertEquals(setOf(firstId, secondId), timelineViewModel.selectedClipIds.value)

            timelineViewModel.undo()
            val restored = timelineViewModel.arrangement.value.clips.associateBy { it.id }
            assertEquals(0L, restored.getValue(firstId).startTick)
            assertEquals(120L, restored.getValue(secondId).startTick)
        }
    }

    @Test
    fun movingClipAtFiveHundredPercentScrollsAndUndoesAsOneTransaction() {
        lateinit var clipId: String
        composeRule.runOnIdle {
            val existingIds = timelineViewModel.arrangement.value.clips.mapTo(hashSetOf()) { it.id }
            timelineViewModel.addPadClip(
                trackIndex = 0,
                startTick = 0L,
                padIndex = 0,
                durationTicks = 1920L,
            )
            clipId = timelineViewModel.arrangement.value.clips.first { it.id !in existingIds }.id
            timelineViewModel.setZoom(5f)
        }
        composeRule.waitForIdle()
        val initialScroll = composeRule.onNodeWithTag("timeline-viewport")
            .fetchSemanticsNode().config[TimelineScrollPxSemanticsKey]
        val viewportBounds = composeRule.onNodeWithTag("timeline-viewport").fetchSemanticsNode().boundsInRoot
        val clipBounds = composeRule.onNodeWithTag("timeline-clip-$clipId").fetchSemanticsNode().boundsInRoot
        val edgePointX = viewportBounds.right - 8f - clipBounds.left

        composeRule.onNodeWithTag("timeline-clip-$clipId").performTouchInput {
            val bodyPoint = Offset(center.x, height - 5f)
            down(bodyPoint)
            moveTo(Offset(edgePointX, bodyPoint.y), delayMillis = 250L)
            moveTo(Offset(edgePointX, bodyPoint.y), delayMillis = 350L)
            up()
        }
        composeRule.waitForIdle()

        val finalScroll = composeRule.onNodeWithTag("timeline-viewport")
            .fetchSemanticsNode().config[TimelineScrollPxSemanticsKey]
        composeRule.runOnIdle {
            assertTrue(finalScroll > initialScroll)
            assertTrue(timelineViewModel.arrangement.value.clips.single { it.id == clipId }.startTick > 0L)
            timelineViewModel.undo()
            assertEquals(0L, timelineViewModel.arrangement.value.clips.single { it.id == clipId }.startTick)
        }
    }

    @Test
    fun pinchDuringMoveCancelsPreviewWithoutCreatingHistory() {
        lateinit var clipId: String
        composeRule.runOnIdle {
            val existingIds = timelineViewModel.arrangement.value.clips.mapTo(hashSetOf()) { it.id }
            timelineViewModel.addPadClip(trackIndex = 0, startTick = 0L, padIndex = 0)
            clipId = timelineViewModel.arrangement.value.clips.first { it.id !in existingIds }.id
            timelineViewModel.selectClip(clipId)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("timeline-clip-$clipId").performTouchInput {
            down(0, center)
            moveTo(0, center + Offset(80f, 0f), delayMillis = 150L)
            down(1, center + Offset(0f, 8f))
            moveTo(0, center + Offset(120f, 0f), delayMillis = 100L)
            moveTo(1, center + Offset(-40f, 8f), delayMillis = 100L)
            up(1)
            up(0)
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val unchanged = timelineViewModel.arrangement.value.clips.single { it.id == clipId }
            assertEquals(0L, unchanged.startTick)
            timelineViewModel.undo()
            assertTrue(timelineViewModel.arrangement.value.clips.none { it.id == clipId })
        }
    }

    @Test
    fun pinchDuringResizeCancelsPreviewWithoutCreatingHistory() {
        lateinit var clipId: String
        var originalDuration = 0L
        composeRule.runOnIdle {
            val existingIds = timelineViewModel.arrangement.value.clips.mapTo(hashSetOf()) { it.id }
            timelineViewModel.addPadClip(trackIndex = 0, startTick = 0L, padIndex = 0)
            val clip = timelineViewModel.arrangement.value.clips.first { it.id !in existingIds }
            clipId = clip.id
            originalDuration = clip.durationTicks
            timelineViewModel.selectClip(clipId)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("timeline-clip-$clipId").performTouchInput {
            val rightHandle = Offset(width - 1f, 1f)
            down(0, rightHandle)
            moveTo(0, rightHandle + Offset(80f, 0f), delayMillis = 150L)
            down(1, Offset(1f, height - 1f))
            moveTo(0, rightHandle + Offset(120f, 0f), delayMillis = 100L)
            moveTo(1, Offset(1f, height - 1f) + Offset(-40f, 0f), delayMillis = 100L)
            up(1)
            up(0)
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val unchanged = timelineViewModel.arrangement.value.clips.single { it.id == clipId }
            assertEquals(originalDuration, unchanged.durationTicks)
            timelineViewModel.undo()
            assertTrue(timelineViewModel.arrangement.value.clips.none { it.id == clipId })
        }
    }

    @Test
    fun invalidPublicTimelineInputsAreRejectedOrClampedWithoutCrashing() {
        composeRule.runOnIdle {
            val initialCount = timelineViewModel.arrangement.value.clips.size

            timelineViewModel.setZoom(Float.NaN)
            timelineViewModel.setZoom(Float.POSITIVE_INFINITY)
            assertTrue(timelineViewModel.zoom.value.isFinite())
            assertTrue(timelineViewModel.zoom.value in 0.2f..5f)

            timelineViewModel.addPadClip(trackIndex = 0, startTick = 0L, padIndex = -1)
            timelineViewModel.addAudioClip(
                trackIndex = 0,
                startTick = 0L,
                path = " ",
                durationTicks = Long.MAX_VALUE,
            )
            assertEquals(initialCount, timelineViewModel.arrangement.value.clips.size)

            timelineViewModel.addPatternClip(trackIndex = 0, startTick = 0L, patternId = -1)
            assertEquals(initialCount, timelineViewModel.arrangement.value.clips.size)

            timelineViewModel.addPatternClip(trackIndex = 0, startTick = 0L, patternId = 0)
            val canonicalPatternClip = timelineViewModel.arrangement.value.clips.last()
            assertEquals(0, (canonicalPatternClip as com.jujidaw.model.PatternClip).patternId)

            timelineViewModel.addPadClip(
                trackIndex = Int.MAX_VALUE,
                startTick = Long.MIN_VALUE,
                padIndex = 0,
                durationTicks = Long.MAX_VALUE,
            )
            val added = timelineViewModel.arrangement.value.clips.last()
            assertEquals(15, added.trackIndex)
            assertEquals(0L, added.startTick)
            assertTrue(added.durationTicks > 0L)
        }
    }

    // ── Selector isolation tests (Módulo 9) ──

    @Test
    fun selectorShowsFiveChips() {
        composeRule.onNodeWithTag("timeline-pad-selector").assertIsDisplayed()
        for (i in 1..5) {
            composeRule.onNodeWithTag("timeline-source-chip-A$i").assertIsDisplayed()
        }
    }

    @Test
    fun selectorCanSelectFirstAndLastPadOptions() {
        composeRule.onNodeWithTag("timeline-source-chip-A1").performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals(0, PadSelectionStore.selectedPad.value)

        composeRule.onNodeWithTag("timeline-pad-selector").performScrollToIndex(31)
        composeRule.onNodeWithTag("timeline-source-chip-B16").performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals(31, PadSelectionStore.selectedPad.value)
    }

    @Test
    fun selectorCanSelectFirstAndLastPatternOptions() {
        composeRule.runOnIdle {
            timelineViewModel.setTool(TimelineTool.DRAW_PATTERN)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("timeline-pattern-selector").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-source-chip-P1").assertIsDisplayed()

        composeRule.onNodeWithTag("timeline-pattern-selector").performScrollToIndex(15)
        composeRule.onNodeWithTag("timeline-source-chip-P16").performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals(15, timelineViewModel.selectedPatternId.value)
    }

    @Test
    fun selectorScrollDoesNotAffectTimelineViewport() {
        val initialSelection = PadSelectionStore.selectedPad.value
        val initialPlayheadX =
            composeRule.onNodeWithTag("timeline-playhead").fetchSemanticsNode().boundsInRoot.left

        composeRule.onNodeWithTag("timeline-pad-selector").performTouchInput {
            swipeLeft(durationMillis = 200L)
        }
        composeRule.waitForIdle()

        val finalPlayheadX =
            composeRule.onNodeWithTag("timeline-playhead").fetchSemanticsNode().boundsInRoot.left
        assertEquals(initialPlayheadX, finalPlayheadX, 0.5f)
        assertEquals(initialSelection, PadSelectionStore.selectedPad.value)
    }

    @Test
    fun padsAndPatternsKeepIndependentScrollState() {
        composeRule.onNodeWithTag("timeline-pad-selector").performScrollToIndex(31)
        composeRule.onNodeWithTag("timeline-source-chip-B16").performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-pad-selector").performScrollToIndex(24)
        val padChipX =
            composeRule.onNodeWithTag("timeline-source-chip-B9").fetchSemanticsNode().boundsInRoot.left

        composeRule.runOnIdle { timelineViewModel.setTool(TimelineTool.DRAW_PATTERN) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-pattern-selector").performScrollToIndex(15)
        composeRule.onNodeWithTag("timeline-source-chip-P16").performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-pattern-selector").performScrollToIndex(8)
        val patternChipX =
            composeRule.onNodeWithTag("timeline-source-chip-P9").fetchSemanticsNode().boundsInRoot.left

        composeRule.runOnIdle { timelineViewModel.setTool(TimelineTool.DRAW_PAD) }
        composeRule.waitForIdle()
        val restoredPadChipX =
            composeRule.onNodeWithTag("timeline-source-chip-B9").fetchSemanticsNode().boundsInRoot.left
        assertEquals(padChipX, restoredPadChipX, 0.5f)
        assertEquals(31, PadSelectionStore.selectedPad.value)

        composeRule.runOnIdle { timelineViewModel.setTool(TimelineTool.DRAW_PATTERN) }
        composeRule.waitForIdle()
        val restoredPatternChipX =
            composeRule.onNodeWithTag("timeline-source-chip-P9").fetchSemanticsNode().boundsInRoot.left
        assertEquals(patternChipX, restoredPatternChipX, 0.5f)
        assertEquals(15, timelineViewModel.selectedPatternId.value)
    }

    @Test
    fun shortHorizontalDragOnSourceChipDoesNotSelectIt() {
        composeRule.onNodeWithTag("timeline-source-chip-A1").performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals(0, PadSelectionStore.selectedPad.value)

        composeRule.onNodeWithTag("timeline-source-chip-A2").performTouchInput {
            down(center)
            moveTo(center - Offset(36f, 0f), delayMillis = 200L)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(0, PadSelectionStore.selectedPad.value)
    }

    @Test
    fun timelineViewportDragDoesNotScrollSelector() {
        composeRule.onNodeWithTag("timeline-pad-selector").performScrollToIndex(31)
        val initialChipX =
            composeRule.onNodeWithTag("timeline-source-chip-B16").fetchSemanticsNode().boundsInRoot.left

        composeRule.onNodeWithTag("timeline-viewport").performTouchInput {
            swipeLeft(durationMillis = 200L)
        }
        composeRule.waitForIdle()

        val finalChipX =
            composeRule.onNodeWithTag("timeline-source-chip-B16").fetchSemanticsNode().boundsInRoot.left
        assertEquals(initialChipX, finalChipX, 0.5f)
    }
}
