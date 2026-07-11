# Task for worker

You are a delegated subagent running from a fork of the parent session. Treat the inherited conversation as reference-only context, not a live thread to continue. Do not continue or answer prior messages as if they are waiting for a reply. Your sole job is to execute the task below and return a focused result for that task using your tools.

Task:
Implement SDD slice F: landscape-navigation for Juji-Synth (Android Kotlin/Compose DAW at /home/osqy/Desktop/juji-synth).

## Goal
Fix the landscape NavigationRail so all 7 tabs are reachable without invisible scrolling. Currently the rail is an 80dp Column with verticalScroll, and the PersistentTransportBar steals height, making SEQUENCER/PROJECT cut off.

## File to modify

### MainScreen.kt
File: app/src/main/java/com/jujidaw/ui/main/MainScreen.kt

Read the full file first. Find the landscape branch (around line 88-114 based on the scout report). It currently looks something like:

```kotlin
// Landscape
Row(Modifier.fillMaxSize()) {
    Column(
        Modifier.width(80.dp).verticalScroll(rememberScrollState())
    ) {
        PersistentTransportBar(...)  // 44dp, steals rail height
        tabs.forEach { tab ->
            NavigationRailItem(...)
        }
    }
    // Content
    currentTab(...)
}
```

## Changes needed

Replace the landscape rail with a proper layout that:
1. Puts the transport bar OUTSIDE the scrollable rail column — in a separate area (top of the rail, or in a Row beside the rail)
2. Makes the rail scrollable with a VISIBLE scroll indicator (Scrollbar or peek affordance)
3. All 7 tabs are reachable — if they don't all fit, show a scroll indicator or overflow

### Implementation approach

Use a `Column` with fixed height (fillMaxHeight) containing:
- Top section: PersistentTransportBar (fixed height, not scrolled)
- Bottom section: scrollable NavigationRail items with a thin scrollbar indicator

```kotlin
// Landscape
Row(Modifier.fillMaxSize()) {
    Column(Modifier.width(80.dp).fillMaxHeight()) {
        // Transport bar — fixed, not scrolled
        PersistentTransportBar(
            isPlaying = ...,
            isRecording = ...,
            onPlay = ...,
            onRecord = ...,
            onReset = ...,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Navigation rail — scrollable with visible indicator
        Box(Modifier.weight(1f)) {
            val scrollState = rememberScrollState()
            Column(Modifier.verticalScroll(scrollState)) {
                tabs.forEach { tab ->
                    NavigationRailItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 10.sp) }
                    )
                }
            }
            // Scrollbar indicator (thin bar on the right edge)
            if (scrollState.maxValue > 0) {
                val thumbHeight = ... // proportional to visible/total
                val thumbTop = scrollState.value.toFloat() / scrollState.maxValue * ...
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .width(3.dp)
                        .height(thumbHeight.dp)
                        .offset(y = thumbTop.dp)
                        .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(1.5f))
                )
            }
        }
    }
    // Content fills remaining width
    Box(Modifier.weight(1f)) {
        currentTab(...)
    }
}
```

The key improvements:
- Transport bar is in a fixed section above the scrollable rail
- A thin scrollbar thumb shows the user that more items exist and where they are
- The rail items use smaller text/icons to fit more in view

## Constraints
- Do NOT modify any file other than MainScreen.kt
- Do NOT break portrait mode — only change the landscape branch
- Keep the same tabs list (TIMELINE, MIXER, SYNTH, PADS, KEYBOARD, SEQUENCER, PROJECT)
- Keep the same navigation behavior (clicking a tab shows its content)
- Run `./gradlew :app:testDebugUnitTest --no-daemon` after changes

## Acceptance criteria
- Landscape rail has transport bar separated from scrollable tab list
- All 7 tabs are reachable (scrollable with visible indicator)
- Transport bar doesn't steal rail column height
- Portrait mode unchanged
- Tests pass

## Acceptance Contract
Acceptance level: checked
Completion is not accepted from prose alone. End with a structured acceptance report.

Criteria:
- criterion-1: Implement the requested change without widening scope

Required evidence: changed-files, tests-added, commands-run, residual-risks, no-staged-files

Finish with a fenced JSON block tagged `acceptance-report` in this shape:
Use empty arrays when no items apply; array fields contain strings unless object entries are shown.
```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "specific proof"
    }
  ],
  "changedFiles": [
    "src/file.ts"
  ],
  "testsAddedOrUpdated": [
    "test/file.test.ts"
  ],
  "commandsRun": [
    {
      "command": "command",
      "result": "passed",
      "summary": "short result"
    }
  ],
  "validationOutput": [
    "validation output or concise summary"
  ],
  "residualRisks": [
    "none"
  ],
  "noStagedFiles": true,
  "diffSummary": "short description of the diff",
  "reviewFindings": [
    "blocker: file.ts:12 - issue found, or no blockers"
  ],
  "manualNotes": "anything else the parent should know"
}
```