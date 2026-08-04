# R4 Review and Closure

Reviewer: independent `review_r1_followup`
Verdict: PASS
Commit: `1c7e6e2 fix(timeline): preserve selector scroll isolation`

The reviewer-required test correction preserved non-centered B9/P9 coordinates
across mode switches; a real 36px drag over A2 verifies no accidental select.

Evidence: `testDebugUnitTest`, `compileDebugKotlin`, `lintDebug`, and
`assembleDebug` PASS; Android instrumentation 19/19 PASS on SM-G998W Android
15. Oboe and `.commandcode/` were excluded. R4 is closed; M10 is next.
