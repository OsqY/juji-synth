# Timeline Hardening — Delivery Plan

Every phase follows `DEFINE → ANALYZE → PLAN → IMPLEMENT_PHASE → VALIDATE →
REVIEW → CLOSE_PHASE`. Four Gradle gates and independent review are required.

## Closed prerequisites

R1 `e1c2f71`, R2 `8f25d30`, R3 `5d93af4`, and R4 `1c7e6e2` are closed.

## Remaining phases

| Phase | Objective | Commit | Closure |
| --- | --- | --- | --- |
| M10 | Edge auto-scroll while moving clips; 100%/500%, bounds, cancel, one undo | `feat(timeline): add edge auto-scroll while moving clips` | AC-F1–F4, gates, device, review PASS |
| M11 | Non-intercepting editing-state indicators | `feat(timeline): add editing state indicators` | AC-V1, gates, review PASS |
| M12 | Device/density validation matrix | `test(timeline): add device density validation matrix` | AC-D1, evidence review |
| M13 | Measured viewport performance/recomposition work | `perf(timeline): reduce viewport recomposition overhead` | AC-P1, before/after evidence |
| M14 | Document viewport/tick model and constraints | `docs(timeline): document viewport-based rendering model` | AC-C1, docs review |
| M15 | Final audit and authorized handoff | optional `chore(timeline): finalize validation and review artifacts` | AC-O1, final-audit complete/blocked |

Current phase: M13. M10 is closed in `22b0461`, M11 in `7f6de85`, and M12 in
the device-matrix commit. The next phase is performance and recomposition
measurement.
