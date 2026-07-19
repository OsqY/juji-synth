# Timeline interactions — e37s15 handoff

## Acceptance status

- [x] Select pads and patterns directly from Timeline with a horizontal source strip.
- [x] Start Timeline on a 1/16 grid and place the first pad at one grid cell.
- [x] Keep adjacent pad clips at their musical width so sixteenth-note entries do not overlap.
- [x] Move selected clips directly and resize from either edge.
- [x] Zoom Timeline with a two-finger pinch anchored to the gesture position.
- [x] Preserve existing copy, duplicate, mute, delete/trash, autosave, scroll, and zoom-button flows.
- [x] Keep one-finger horizontal scrolling independent from two-finger pinch zoom.
- [x] Resolve ruler, scrub, and placement taps from the measured viewport plus scroll offset.
- [x] Keep the yellow scrub row viewport-wide while musical content uses its required width.
- [x] Preview resize continuously from explicit upper-edge handles.
- [x] Delete short clips with a tap-or-scrub Delete tool.
- [x] Undo and redo timeline edits from touch controls or keyboard shortcuts.
- [x] Show exactly five larger pad/pattern choices inside an independently scrolling source box.

## Workflow handoff

- Notion: the task is published as **En curso**; the database has no separate review status, so the page notes that it is ready for review.
- Linear: **OSQ-5** is published as **In Review**; move it to **Done** after approval.
- GitHub: use the acceptance list above in the PR body and attach the Gradle test/lint/build results.
- Security review: verify bounded pad/pattern indexes, bounded zoom, snapped non-negative ticks, and no new permissions or dependencies.

Notion and Linear write access are available. The private GitHub repository is not exposed to the current connector, so the PR remains pending.
