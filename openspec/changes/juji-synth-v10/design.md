## Context

The root cause chain: same-note re-trigger → duplicate voice → noteOff only finds first match → second voice stuck in sustain forever → all 4 voices consumed → no new notes playable → stuck voices respond to preset parameter changes → sounds like presets "play themselves."

## Goals / Non-Goals

**Goals:**
- Pressing the same note twice in quick succession doesn't create a stuck voice
- noteOff releases ALL voices playing a given note, not just the first one
- Previously stuck voices no longer interfere with preset loading

**Non-Goals:**
- No new features
- No UI changes

## Decisions

### 1. Fix noteOn: Silence existing same-note voices

**Decision:** Before allocating a new voice in `noteOn()`, iterate all active voices and `stopImmediately()` any that are playing the same MIDI note. Also set `voiceActive_[i] = false` to free the slot.

```cpp
void AudioEngine::noteOn(int midiNote, int velocity) {
    // Re-trigger: silence any existing voice already playing this note
    for (int i = 0; i < MAX_VOICES; i++) {
        if (voiceActive_[i] && voices_[i].getNote() == midiNote) {
            voices_[i].stopImmediately();
            voiceActive_[i] = false;
        }
    }
    
    int voiceIdx = allocateVoice();
    if (voiceIdx >= 0) {
        voices_[voiceIdx].init(sampleRate_);
        applyModulationMatrix();
        voices_[voiceIdx].noteOn(midiNote, velocity);
        voiceActive_[voiceIdx] = true;
    }
}
```

**Rationale:** This is standard synth behavior — playing the same note re-triggers it (re-starts the envelope from attack). Any duplicate voices are cleaned up before the new note plays.

### 2. Fix noteOff: Release ALL matching voices

**Decision:** Change `noteOff` to find and release ALL voices playing the MIDI note, not just the first one.

```cpp
void AudioEngine::noteOff(int midiNote) {
    for (int i = 0; i < MAX_VOICES; i++) {
        if (voiceActive_[i] && voices_[i].getNote() == midiNote) {
            voices_[i].noteOff();
        }
    }
}
```

**Rationale:** If somehow two voices are playing the same note (race condition, edge case), both should be released. This is a safety net.

## Migration Plan

1. Fix noteOn in AudioEngine.cpp
2. Fix noteOff in AudioEngine.cpp
3. Build and verify