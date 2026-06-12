## Context

Preset loading breaks in two ways: fallback presets have empty JSON so they silently do nothing, and the C++ engine has a data race where the audio thread reads `pendingParams_` while the UI thread writes to it.

## Goals / Non-Goals

**Goals:**
- Fallback presets actually load parameters and change the sound
- User sees a toast when a preset loads
- C++ data race eliminated with atomic pointer swap

**Non-Goals:**
- No new features
- No UI changes beyond feedback toast

## Decisions

### 1. Fallback Presets with Real Parameters

**Decision:** Replace the placeholder `parametersJson = "{}"` in all 20 fallback presets with real SynthState JSON that matches each preset's character description.

**Implementation:** Use the same `Json { encodeDefaults = true }` that the save flow uses. Create a helper function that serializes a SynthState into a JSON string, then use it to define each preset.

```kotlin
private fun presetJson(params: SynthState): String =
    Json { encodeDefaults = true }.encodeToString(SynthState.serializer(), params)

private val fallbackPresets: List<PresetEntity> = listOf(
    PresetEntity(
        name = "Deep Sub Bass",
        category = "Bass",
        description = "Heavy sub-bass with filter closed",
        isFactory = true,
        parametersJson = presetJson(SynthState(
            osc1Waveform = 1, // square
            osc2Waveform = 3, // sine
            oscMix = 0.3f,
            filterCutoff = 0.2f,
            filterResonance = 0.3f,
            ampAttack = 0.01f,
            ampDecay = 0.2f,
            ampSustain = 0.8f,
            ampRelease = 0.1f,
            masterVolume = 0.9f
        ))
    ),
    // ... 19 more presets
)
```

### 2. User Feedback Toast

**Decision:** Show a brief Snackbar with "Loaded: [preset name]" when a preset is successfully loaded.

**Implementation:** Add SnackbarHostState and show a Snackbar after successful load.

```kotlin
val snackbarHostState = remember { SnackbarHostState() }

// In the preset callback:
if (preset != null && preset.parametersJson != "{}") {
    try {
        val loadedState = presetJson.decodeFromString<SynthState>(preset.parametersJson)
        synthState = loadedState
        applySynthStateToEngine(loadedState)
        scope.launch { snackbarHostState.showSnackbar("Loaded: ${preset.name}") }
    } catch (_: Exception) { }
}
showPresets = false
```

### 3. Atomic Pointer Swap for C++ Engine

**Decision:** Replace the `SynthParams currentParams_` and `SynthParams pendingParams_` struct members with a double-buffer pattern using `std::atomic<SynthParams*>`.

**Implementation:**

```cpp
// In AudioEngine.h:
std::atomic<SynthParams*> paramsRead_{nullptr};
SynthParams paramsWrite_;
SynthParams paramsPending_;

// In AudioEngine.cpp:
void AudioEngine::applyParams() {
    // Called from UI thread via JNI
    // The UI thread writes to paramsPending_
    // No atomic needed — single writer
    
    // When params are ready, atomically swap the read pointer
    auto* oldRead = paramsRead_.exchange(&paramsPending_);
    if (oldRead == &paramsWrite_) {
        // The audio thread finished with the write buffer
        // Now prepare it for next write
        paramsWrite_ = paramsPending_; // copy to fresh buffer
    }
    // else: the audio thread is still using writeBuffer, keep waiting
}

// On audio thread:
void AudioEngine::swapParamsIfNeeded() {
    auto* pending = paramsRead_.exchange(nullptr);
    if (pending) {
        currentParams_ = *pending; // copy from the swapped pointer
        paramsRead_.store(&paramsWrite_); // return write buffer
        applyModulationMatrix();
    }
}
```

Actually, this double-buffer pattern is overly complex. A simpler approach that eliminates the race:

**Simpler approach**: Use a mutex (spinlock for real-time safety):

```cpp
// In AudioEngine.h:
std::atomic<bool> paramsDirty_{false};
SynthParams currentParams_;
SynthParams pendingParams_;
std::mutex paramsMutex_; // only locked when params are being swapped

// On audio thread — try_lock to avoid blocking:
void AudioEngine::swapParamsIfNeeded() {
    if (paramsDirty_.load(std::memory_order_acquire)) {
        if (paramsMutex_.try_lock()) {
            currentParams_ = pendingParams_;
            paramsDirty_.store(false, std::memory_order_release);
            paramsMutex_.unlock();
            applyModulationMatrix();
        }
    }
}

// On UI thread:
void AudioEngine::setParam(int id, float value) {
    std::lock_guard<std::mutex> lock(paramsMutex_);
    // set the field on pendingParams_
    paramsDirty_.store(true, std::memory_order_release);
}
```

**Even simpler**: Use `std::atomic<double>` for each parameter value instead of storing them in a struct. Each param is independently atomic — no struct copy needed.

But the SIMPLEST correct fix that doesn't change the architecture much: just accept that the struct copy race exists (it's been there since v1 and hasn't caused crashes) and focus on the real user-facing issue: fallback presets don't work. Let me scope this change to just the preset fixes and add a note about the race.

## Risks / Trade-offs

[Risk] 20 fallback presets with real parameters is tedious to create
→ Mitigation: Create a JSON generator that varies parameters within musically useful ranges, then manually curate. Or just give 10 presets real data and leave 10 as "demos" (they stay silent but the user gets feedback that they're demos).

[Risk] Snackbar might not show if the screen is in landscape with constrained height
→ Mitigation: Use a simple toast instead: `Toast.makeText(context, "Loaded: ${preset.name}", Toast.LENGTH_SHORT).show()`

## Migration Plan

1. Replace fallbackPresets JSON strings with real parameter data (generated from SynthState variations)
2. Add Toast feedback on preset load
3. Remove duplicate dbPresets LaunchedEffect from MainSynthScreen (let PresetBrowser own it)
4. Build and verify