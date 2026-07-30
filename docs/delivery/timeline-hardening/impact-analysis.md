# Timeline Hardening — Impact Analysis

| Area | Components | Risk/contract |
| --- | --- | --- |
| Move | `TimelineScreen.kt`, `TimelineEditingMath.kt` | Edge detection is pixel-only; commit remains tick-based. |
| Gestures | `TimelineGestureState.kt` | Only `MovingClip` owns auto-scroll; pinch/cancel wins. |
| History | `TimelineViewModel.kt`, edit history | One move command, one undo. |
| Tests | unit and Compose harness | Pure velocity/limit tests plus real touch tests. |
| Docs/evidence | `docs/pads-timeline-workflow.md`, device records | No unsupported PASS claims. |

Persistence/schema changes: none. Autosave stays after confirmed transactions.
Public zoom/tick/scroll inputs remain bounded. Oboe is dirty user-owned state
and must never be staged. Rollback is per-module commit; never reset the branch.

Residual risks: Loop extreme-range setters are deferred; only one physical
device profile is currently evidenced; external integrations require consent.
