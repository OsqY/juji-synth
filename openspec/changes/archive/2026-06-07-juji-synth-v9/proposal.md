## Why

Preset loading in v8 has a critical failure path: fallback presets have `parametersJson = "{}"` which is explicitly filtered out by the loading code, making 20/20 fallback presets do nothing. DB presets from `FactoryPresetSeeder` work when loaded, but the 34 rapid JNI calls create a C++ data race on the audio thread (`currentParams_ = pendingParams_` copy while UI thread writes to `pendingParams_`). Additionally, there's no user feedback when a preset is loaded — the dialog just closes silently.

## What Changes

- **Give fallback presets real parameters**: Replace `"{}"` with actual `SynthState` JSON matching each preset's character (e.g., "Deep Sub Bass" → low cutoff, high osc levels, closed filter)
- **Add user feedback on preset load**: Show a brief Snackbar or toast "Loaded: [preset name]" when a preset is successfully loaded
- **Fix C++ data race**: Replace the racy `currentParams_ = pendingParams_` struct copy with an atomic pointer swap (double-buffer pattern using `std::atomic<SynthParams*>`)

## Capabilities

### Modified Capabilities

- `preset-system`: Fallback presets now have real parameter JSON. Loading shows feedback toast.
- `multi-touch-fix-v2`: C++ data race fixed with atomic pointer swap.

## Impact

- **PresetBrowser.kt**: Replace all `"{}"` in fallback presets with real SynthState parameter JSON strings
- **MainSynthScreen.kt**: Add Snackbar/Toast "Loaded: [name]" after successful preset load. Remove duplicate dbPresets LaunchedEffect (let PresetBrowser own it).
- **AudioEngine.h**: Change `SynthParams currentParams_` and `SynthParams pendingParams_` to atomic pointer double-buffer
- **AudioEngine.cpp**: Rewrite `swapParamsIfNeeded` to atomically swap pointers instead of copying the struct