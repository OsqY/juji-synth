# Timeline Design Contract

## Summary

The Timeline is a viewport-based arrangement editor for pads, patterns, and
audio clips. Its primary design requirement is reliable musical editing under
scroll and zoom, rather than adding further controls.

## Architecture

```text
Arrangement clips and transport state (ticks)
                |
                v
        TimelineTransform
                |
     +----------+-----------+------------+
     v          v           v            v
 visible grid  ruler     visible clips  playhead
                |
                v
       Compose viewport pixels
```

- Clips store `startTick`, `durationTicks`, and `trackIndex`; they do not store
  pixel coordinates.
- `TimelineTransform` maps ticks to viewport pixels and maps input pixels back
  to ticks.
- Grid, ruler, clips, and playhead render from the visible tick range. Active
  drag and resize clips remain composed even if their source position moves out
  of range.
- For finite inputs, zoom is anchored to the gesture centroid and horizontal
  scroll is clamped to the virtual musical extent. Non-finite inputs are an
  active hardening gap.

## Interaction contract

Gesture priority is pinch, delete, resize handle, clip body, ruler scrub, then
empty Timeline background.

- Clip body drags move clips; resize starts only from a handle zone.
- A completed move, resize, delete, duplicate, paste, mute, or restoration is
  one undo/redo transaction.
- Delete collects unique clip IDs through one stroke, then commits once.
- The pad and pattern selector owns its horizontal scroll and must not move the
  Timeline viewport.

## Current phase order

1. Preserve multi-selection when beginning and completing clip moves.
2. Make pinch cancellation safe for active move and resize gestures.
3. Make Timeline arithmetic total for large, invalid, and non-finite inputs.
4. Restore selector scroll behavior and make its UI tests meaningful.
5. Continue the remaining modules in `docs/timeline-hardening-plan.md` only
   after each phase passes validation and independent review.

## Known limitations

- Group move, pinch cancellation, extreme coordinate values, and selector
  scroll restoration are currently under active hardening and are not accepted
  as complete.
- Auto-scroll during clip movement, device-density evidence, performance
  profiling, and final security/PR closure remain future modules.
- The Oboe submodule is excluded from Timeline work.

## Validation

Required per phase:

```bash
./gradlew testDebugUnitTest
./gradlew compileDebugKotlin
./gradlew lintDebug
./gradlew assembleDebug
```

Run `./gradlew connectedDebugAndroidTest` when an ADB-connected device or
emulator is available.
