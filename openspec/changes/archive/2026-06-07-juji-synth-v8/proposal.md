## Why

V7's velocity sensitivity feature introduced a critical regression: every key press gets stuck because `onNoteOff` retriggers the note via `noteOff + noteOn` without sending a follow-up `noteOff`. The re-triggered note's ADSR envelope stays in sustain forever, consuming a voice that is never freed. After 4 presses, all voices are stuck. Additionally, the C++ voice stealing always steals voice 0 regardless of age, which can cut off voices mid-release.

## What Changes

- **Remove velocity retrigger**: Replace the `noteOff + noteOn` in `onNoteOff` with a simple `noteOff(note)`. Velocity stays at 100 for all notes. This fixes all stuck notes.
- **Improve voice stealing**: Track voice age via a counter in `SynthVoice`. In `allocateVoice()`, steal the oldest voice instead of always voice 0.
- **Set up git**: Initialize git repository with proper `.gitignore` for Android/Kotlin/C++ projects.

## Capabilities

### Modified Capabilities

- `velocity-sensitivity`: REMOVED — velocity retrigger causes stuck notes with ADSR envelopes. All notes play at velocity 100.
- `multi-touch-fix-v2`: Now works correctly without velocity interference.

## Impact

- **MainSynthScreen.kt**: Remove `notePressTimes` state, simplify `onNoteOff` to just `SynthEngine.noteOff(note)`. Remove import for Map if unused.
- **SynthVoice.h/cpp**: Add `age_` counter incremented each time the voice is used. Add `getAge()` accessor.
- **AudioEngine.cpp**: Replace voice-0 stealing with oldest-voice stealing in `allocateVoice()`.
- **New**: `.gitignore` file at project root.