# Repository Conventions

## Stack and style

- Kotlin on Android API 31+, Jetpack Compose, JUnit 4, and Compose UI tests.
- Follow surrounding Kotlin style: immutable values, `StateFlow` for observable
  state, pure internal helpers for Timeline math, and focused composables.
- Keep musical data in `Long` ticks. Clamp or reject invalid values at state
  boundaries; avoid unchecked arithmetic for clip end positions or durations.
- Prefer stable clip IDs and stable Compose list keys.

## Timeline interaction conventions

- `TimelineTransform` is the coordinate source of truth. Do not duplicate
  tick/pixel formulas in UI components.
- A gesture claims one operation: scroll, pinch, move, resize, delete, or
  scrub. A replacement gesture must cancel the old operation without committing
  its preview.
- Capture a gesture's target IDs when it begins. Do not read mutable selection
  state later to decide what a completed gesture changes.
- Keep previews local until gesture completion. Persist and autosave only after
  a completed transaction.

## Testing conventions

- Add unit tests for pure coordinate, snapping, state-machine, and edit-history
  behavior.
- Add instrumentation tests for Compose semantics and real touch behavior.
- Tests must use deterministic Timeline state; never depend on real autosave or
  project data.
- A green compile or lint result is not evidence of correct gesture behavior.

## Git and review conventions

Before any Git or GitHub command, read this file and inspect:

```bash
git status --short
git diff --submodule=log -- app/src/main/cpp/oboe
```

- Never stage `app/src/main/cpp/oboe`.
- Never use `git add -A` or broad staging commands. Stage the reviewed files by
  explicit path.
- One phase produces one focused conventional commit after validation and
  independent review.
- Do not push, open a PR, merge, or update external trackers unless the user
  requests it for that phase.

## Validation commands

Run after every completed phase:

```bash
./gradlew testDebugUnitTest
./gradlew compileDebugKotlin
./gradlew lintDebug
./gradlew assembleDebug
```

When ADB has a target device:

```bash
adb devices -l
./gradlew connectedDebugAndroidTest
```

Record skipped device validation honestly; do not mark it as passed.
