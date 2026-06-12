## Why

When the same MIDI note is pressed twice in quick succession, `noteOn` allocates a new voice without releasing the old one playing the same note. `findVoiceByNote` returns only the first match on `noteOff`, so the second voice never receives noteOff and stays in sustain forever. After 4 such events, all voices are consumed — no new notes can play. Previously stuck voices also respond to new preset parameters, making it seem like presets "start playing by themselves."

## What Changes

- **Fix re-trigger**: In `AudioEngine::noteOn()`, before allocating a new voice, iterate all active voices and silence any that are playing the same MIDI note. This prevents duplicate voices for the same note.
- **Fix findVoiceByNote**: Return ALL matching voices so noteOff can release all of them.

## Capabilities

### Modified Capabilities
- `multi-touch-fix-v2`: C++ noteOn now silences existing voices playing the same note before allocating a new voice

## Impact
- **AudioEngine.cpp**: Rewrite `noteOn()` to find and silence existing same-note voices. Rewrite `noteOff()` to find and release ALL matching voices.
- **AudioEngine.h**: No changes needed.