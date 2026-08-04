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
- Zoom is anchored to the gesture centroid and horizontal scroll is clamped to
  the virtual musical extent. Transform and model boundaries sanitize
  non-finite/extreme values before they reach rendering or editing state.

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

The implementation phases through M13 are closed on `feat/timeline-hardening`:
multi-clip moves, safe pinch cancellation, bounded timeline arithmetic,
selector isolation, edge auto-scroll, editing indicators, physical-device
evidence, and viewport composition work all have independent validation and
review records under `docs/delivery/timeline-hardening/`.

The remaining work is documentation (M14), then the final audit and explicitly
authorized PR, security, Linear, and Notion handoff (M15).

## Known limitations

- Additional device-density and orientation profiles beyond the physical
  SM-G998W evidence remain unvalidated.
- The viewport tests demonstrate bounded composition, but do not claim a frame
  time or FPS improvement without a device profiler trace.
- Final security review and external PR/Linear/Notion closure require the
  authorized M15 handoff.
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
