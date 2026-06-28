## Context

The audio engine has a data race: `noteOn()`/`noteOff()` from the UI thread directly modify voice state while `processAudio()` on the audio thread reads it. This causes torn reads, corrupted state, and eventual audio cutoff. The fix uses a lock-free SPSC (Single Producer, Single Consumer) queue to decouple the threads.

## Goals / Non-Goals

**Goals:**
- Eliminate all data races between UI and audio threads
- Wire LFO modulation and sub-oscillator (dead code)
- Consolidate preset param loading into a single atomic swap

**Non-Goals:**
- No new synthesis features
- No UI changes

## Decisions

### 1. Lock-Free Note Queue

**Decision:** Use a fixed-size ring buffer (`std::array<NoteEvent, 64>` with atomic head/tail) to pass note events from UI thread to audio thread.

```cpp
struct NoteEvent {
    enum Type { NoteOn, NoteOff } type;
    int note;
    int velocity;
};

// AudioEngine.h:
std::array<NoteEvent, 64> noteQueue_;
std::atomic<int> noteQueueHead_{0};
std::atomic<int> noteQueueTail_{0};

// AudioEngine.cpp — UI thread (JNI):
void AudioEngine::noteOn(int midiNote, int velocity) {
    int tail = noteQueueTail_.load(std::memory_order_relaxed);
    int nextTail = (tail + 1) % 64;
    if (nextTail != noteQueueHead_.load(std::memory_order_acquire)) {
        noteQueue_[tail] = {NoteEvent::NoteOn, midiNote, velocity};
        noteQueueTail_.store(nextTail, std::memory_order_release);
    }
}

// AudioEngine.cpp — audio thread:
void AudioEngine::processNoteQueue() {
    while (noteQueueHead_.load(std::memory_order_acquire) != 
           noteQueueTail_.load(std::memory_order_acquire)) {
        int head = noteQueueHead_.load(std::memory_order_relaxed);
        auto& event = noteQueue_[head];
        if (event.type == NoteEvent::NoteOn) {
            // Direct voice allocation + setup (ON audio thread now)
            handleNoteOn(event.note, event.velocity);
        } else {
            handleNoteOff(event.note);
        }
        noteQueueHead_.store((head + 1) % 64, std::memory_order_release);
    }
}

// In processAudio(), at the very start:
void AudioEngine::processAudio(...) {
    processNoteQueue();  // ← drain all pending note events
    swapParamsIfNeeded();
    ...
}
```

### 2. Wire Dead Code

**LFO modulation:** In `processAudio()`, after computing `modSources`, apply them to the current sample via a simple modulation matrix lookup.

**Sub-oscillator:** Add `if (subOscLevel_ > 0.0f) sample += generateSubOsc(currentFreq) * subOscLevel_` to the voice mixing section. (Need to track the current base frequency.)

### 3. Consolidated Params

**Decision:** Add a `nativeSetAllParams(jsonParams: String)` JNI method that deserializes all params at once on the C++ side and does a single atomic swap.

Or simpler: pass all 34 params as a float array in one JNI call, set them all on `pendingParams_`, then flip `paramsPending_` once. This avoids 34 separate JNI overhead calls and 34 potential partial swaps.

## Risks / Trade-offs

[Risk] Lock-free queue with fixed size (64) could overflow if UI thread floods events
→ Mitigation: 64 entries = 64 note events between audio callbacks. At 5.8ms per callback and even the fastest human playing (~10 notes/second), this will never overflow.

[Risk] Lock-free atomics are architecture-dependent
→ Mitigation: Standard C++ `std::atomic` with `memory_order_acquire`/`release` works on all Android architectures (ARM, x86, x86_64).

## Migration Plan

1. Add NoteEvent struct + lock-free queue to AudioEngine
2. Move noteOn/noteOff logic to handleNoteOn/handleNoteOff (called from audio thread)
3. Drain queue at start of processAudio()
4. Wire LFO modulation + sub-oscillator
5. Consolidate applySynthStateToEngine into single JNI call
6. Build and test