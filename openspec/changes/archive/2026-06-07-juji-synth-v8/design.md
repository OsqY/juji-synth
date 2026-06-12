## Context

V7's velocity sensitivity feature causes stuck notes on every press. The fix is simple: remove the `noteOff + noteOn` retrigger and replace with just `noteOff`. Voice stealing can also be improved by tracking voice age.

## Goals / Non-Goals

**Goals:**
- Fix all stuck notes (remove velocity retrigger)
- Improve voice stealing to steal oldest voice instead of voice 0
- Initialize git repo with proper .gitignore

**Non-Goals:**
- No new features
- No UI changes

## Decisions

### 1. Remove Velocity Retrigger

**Decision:** Delete `notePressTimes` state. Simplify `onNoteOff` to just `SynthEngine.noteOff(note)`. Keep initial velocity at 100.

**Rationale:** Velocity retrigger is fundamentally incompatible with ADSR envelopes. Once a note is released and retriggered, no subsequent noteOff arrives to release it. The envelope stays in sustain forever. Removing it completely is the safest fix.

### 2. Voice Age Tracking

**Decision:** Add a `uint64_t age_` counter to `SynthVoice` that increments on each `noteOn`. In `allocateVoice()`, find the voice with the highest age value and steal that one.

```cpp
// In SynthVoice.h:
uint64_t age_ = 0;
uint64_t getAge() const { return age_; }

// In SynthVoice.cpp, noteOn():
age_++;

// In AudioEngine.cpp, allocateVoice():
int AudioEngine::allocateVoice() {
    // Find free voice
    for (int i = 0; i < MAX_VOICES; i++) {
        if (!voiceActive_[i]) return i;
    }
    // Steal oldest (highest age)
    int oldest = 0;
    uint64_t maxAge = voices_[0].getAge();
    for (int i = 1; i < MAX_VOICES; i++) {
        if (voices_[i].getAge() > maxAge) {
            maxAge = voices_[i].getAge();
            oldest = i;
        }
    }
    voices_[oldest].stopImmediately();
    return oldest;
}
```

### 3. Git Setup

Standard Android `.gitignore` covering: build outputs, local properties, IDE files, native build artifacts, and user-specific files.

## Migration Plan

1. Fix onNoteOff in MainSynthScreen.kt
2. Improve voice stealing in AudioEngine.cpp + SynthVoice
3. Create .gitignore
4. Build and verify
5. Init git repo