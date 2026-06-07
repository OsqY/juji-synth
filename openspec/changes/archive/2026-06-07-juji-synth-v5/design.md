## Context

V4 introduced two regressions:

1. **Tooltip overlay blocks dragging** (Components.kt). A transparent `Box` with `detectTapGestures(onLongPress)` was placed on top of each knob. Being drawn after the knob in the composition order, it intercepts all touch events before the knob's `detectDragGestures` can process them. Neither drag nor long-press works reliably — both gesture detectors compete for the same pointer.

2. **Divider sizing broken** (EffectsPanel.kt). The dividers were changed from `height(180.dp)` to `fillMaxHeight(0.8f)`. `fillMaxHeight` inside a `verticalScroll` Column has undefined behavior because the scrollable column has infinite measured height — `0.8f` of infinity produces unusable values.

## Goals / Non-Goals

**Goals:**
- Knobs respond to drag IMMEDIATELY (no delay, no gesture competition)
- Long-press on knob shows tooltip popup after 500ms hold WITHOUT moving
- Tooltip auto-dismisses after 2 seconds
- EffectsPanel dividers render correctly at a fixed height
- Octave display is visible to confirm changes

**Non-Goals:**
- No new synthesis features
- No UI layout overhaul

## Decisions

### 1. Combined Gesture: `awaitEachGesture` in a Single `pointerInput`

**Decision:** Remove the separate overlay Box. Move tooltip logic into the existing knob's `pointerInput` block using `awaitEachGesture` that detects both drag and long-press.

```kotlin
.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downTime = System.currentTimeMillis()
        var dragMode = false
        
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.find { it.id == down.id } ?: break
            
            if (!change.pressed) { change.consume(); break }
            
            val elapsed = System.currentTimeMillis() - downTime
            val distance = (change.position - down.position).getDistance()
            
            when {
                // 500ms hold without movement → long-press = tooltip
                !dragMode && elapsed > 500 && distance < 8.dp.toPx() -> {
                    showTooltip = true
                    change.consume()
                    break  // exit — tooltip shows, no drag
                }
                // Finger moved significantly → drag mode
                distance > 8.dp.toPx() -> {
                    dragMode = true
                    showTooltip = false
                }
            }
            
            if (dragMode) {
                val delta = -(change.position.y - down.position.y) / 200f
                onValueChange((currentValue + delta).coerceIn(minValue, maxValue))
            }
            
            change.consume()
        }
    }
}
```

**Rationale:** Single gesture handler eliminates competition. Down → wait 500ms → if finger moved: drag, if finger still: tooltip. The knob's previous `detectDragGestures` is replaced by this.

### 2. Fixed Divider Heights

**Decision:** Replace `fillMaxHeight(0.8f)` with `height(120.dp)` on EffectsPanel dividers. Add `Modifier.height(IntrinsicSize.Min)` to the effects Row so dividers span the row height naturally.

**Rationale:** Fixed height works reliably inside scrollable layouts. 120dp is tall enough to span 2-3 rows of small knobs without wasting space.

### 3. Octave Display Enhancement

**Decision:** Keep the existing octave buttons but also show the current octave range label (e.g., "C3-B4") next to the octave display text so users can visually confirm the change.

## Risks / Trade-offs

[Risk] `awaitEachGesture` replaces `detectDragGestures` — subtle behavior differences
→ Mitigation: The delta calculation is the same (`-dy/200f`). Test to ensure drag feels identical to before.

[Risk] 500ms long-press timeout might feel too short or too long
→ Mitigation: 500ms is the standard Android long-press duration. Users can adjust in future versions.

## Migration Plan

1. Rewrite SynthKnob gesture handler (remove overlay, add combined handler)
2. Fix EffectsPanel divider heights
3. Build and verify knobs drag immediately
4. Build and verify long-press shows tooltip
5. Build and verify EffectsPanel renders correctly
6. Device test