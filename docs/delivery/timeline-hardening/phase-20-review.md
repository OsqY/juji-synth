# M20 — Audio metadata and path validation

## Scope and acceptance

AC-S1 requires invalid audio offsets, fades, gains, and project-external paths
to be rejected before native audio loading.

## Implementation

- `AudioClip` rejects negative start offsets, negative fade-in/fade-out values,
  negative gain, `NaN`, and infinite gain values.
- `ProjectRepository.loadProject()` validates every audio reference with the
  canonical project path policy and requires the resolved file to exist before
  returning a loaded project.
- Unit coverage includes invalid metadata, traversal, and a project-local
  symlink that resolves outside the project.
- Instrumentation coverage rejects an absolute audio reference outside the
  project directory.

## Validation

- `./gradlew testDebugUnitTest` — PASS.
- `./gradlew compileDebugKotlin` — PASS.
- `./gradlew lintDebug` — PASS.
- `./gradlew assembleDebug` — PASS.
- `./gradlew compileDebugAndroidTestKotlin` — PASS.
- Focused external-path instrumentation — PASS (1/1).
- `./gradlew connectedDebugAndroidTest` — PASS (28/28) on CoC-API35 / Android 15.
- Final correction retry: `adb devices -l` reported no attached device; the
  connected suite was not rerun after the unit-test-only coverage additions.
- `git diff --check` — PASS.

## Independent review

`/root/m20_followup` and `/root/m18_review` reviewed the corrected diff and
returned PASS with no P0–P3 findings. The reviews confirmed canonical
containment, symlink handling, pre-native-load validation, focused tests, and
unchanged Oboe/`.commandcode`.

## Security review

Verdict: PASS.

- Critical/High/Medium/Low: none.
- No secrets or protected files changed.
- Required fixes: none.

## Closure

M20 is complete. M21 is the next phase: prevent clip-end and export-duration
arithmetic overflow.
