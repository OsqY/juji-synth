# M18 Review — Compose resize coverage

Reviewer: `/root/m18_review`
Verdict: PASS

The initial P2 finding was that the tests asserted the handle tags but sent
fixed parent coordinates (`48f` and `width - 2f`), which was not
density-independent. The tests now assert the unmerged handle tags and start
gestures at clip-relative 5%/95% horizontal and 10% vertical coordinates.
Those points remain inside the existing `20.dp` edge-touch zone and `24.dp`
handle band across densities while keeping the fixture clip in the viewport.

Follow-up evidence:

- Focused API 35 resize run: `OK (2 tests)`.
- Full API 35 `connectedDebugAndroidTest`: `26/26` passed.
- `git diff --check`: PASS.
- No production Timeline code changed; Oboe and `.commandcode` are unchanged.

Final review: no P0–P3 findings remain.
