# Timeline Hardening — Impact Analysis

| Area | Components | Risk/contract |
| --- | --- | --- |
| Move | `TimelineScreen.kt`, `TimelineEditingMath.kt` | Edge detection is pixel-only; commit remains tick-based. |
| Gestures | `TimelineGestureState.kt` | Only `MovingClip` owns auto-scroll; pinch/cancel wins. |
| History | `TimelineViewModel.kt`, edit history | One move command, one undo. |
| Tests | unit and Compose harness | Pure velocity/limit tests plus real touch tests. |
| Docs/evidence | `docs/pads-timeline-workflow.md`, device records | No unsupported PASS claims. |
| Audio persistence | `ProjectRepository.kt`, `ProjectAutosave.kt`, import/save callers | Audio paths are project-relative before persistence; source and target roots are explicit; failed saves do not publish success. |
| Multi-drag preview | `TimelineScreen.kt`, `TimelineEditingMath.kt` | Every captured move ID stays composed during auto-scroll while unrelated clips remain virtualized. |
| Regression coverage | repository tests and Timeline math/Compose tests | Autosave, rename, failure handling, and multi-clip visibility are deterministic and repeatable. |

Persistence/schema changes: no migration. Existing relative-path loading stays
compatible; successful saves and renames normalize legacy in-project absolute
paths. Autosave stays after confirmed transactions. Public zoom/tick/scroll
inputs remain bounded. Oboe is dirty user-owned state and must never be staged.
Rollback is per-module commit; never reset the branch.

Residual risks: legacy audio paths outside the active project require a manual
re-import; loop extreme-range setters are deferred; only one physical device
profile is currently evidenced; external integrations require consent.
