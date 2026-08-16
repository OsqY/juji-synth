# Post-merge core fixes

Base: merged `origin/main` at `01a78f3`.

## Acceptance

- Canonical cutoff, LFO rate, and master-volume automation use native indices 9, 21, and 38.
- A failed save leaves the previously saved project JSON and assets unchanged.
- Loading a legacy absolute pad path persists its project-relative migration.
- Project restore starts only after audio-engine startup and rejects undecodable assets before replacing live transport state.
- Unit, compile, lint, assemble, and available device gates pass.

## Excluded

Timeline feature work, visual changes, dependency changes, native-engine redesign, and `app/src/main/cpp/oboe`.

## Validation

- `./gradlew testDebugUnitTest`: pass.
- `./gradlew compileDebugKotlin`: pass.
- `./gradlew lintDebug`: pass.
- `./gradlew assembleDebug`: pass in a disposable validation copy using the committed Oboe revision.
- `./gradlew connectedDebugAndroidTest`: 39/39 pass on SM-G998W.
- `git diff --check`: pass.

The protected Oboe submodule remained unmodified and uninitialized in the isolated worktree.

## Independent review

- Gauss (`gpt-5.6-luna`): pass after native-pointer and scheduler correction loops.
- Chandrasekhar (`gpt-5.6-luna`): pass for persistence and recovery behavior.
