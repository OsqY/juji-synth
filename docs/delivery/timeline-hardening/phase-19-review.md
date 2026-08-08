# M19 Review — Atomic multi-delete Compose coverage

Reviewer: `/root/m18_review`
Verdict: PASS

The review initially found a P1 coverage gap: the stroke only crossed three
targets once and did not prove that an unrelated clip survives. The test now
creates three targets on track 0 and a fourth clip on track 1, performs the
stroke A→B→A→C, and requires exactly three trash entries. The unrelated clip
must remain after delete, undo, and redo; the three targets must restore and
re-delete as one history transaction.

Evidence:

- Focused API 35 test: `OK (1 test)`.
- Full API 35 `connectedDebugAndroidTest`: `27/27` passed.
- `git diff --check`: PASS.
- Bounds are converted from root coordinates to the Delete overlay, so the
  gesture is independent of density. Oboe and `.commandcode` are unchanged.

Follow-up verdict: no P0–P3 findings remain.
