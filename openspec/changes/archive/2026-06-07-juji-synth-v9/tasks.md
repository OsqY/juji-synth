## 1. Fix Fallback Presets

- [x] 1.1 In PresetBrowser.kt, create a helper function that serializes a SynthState to JSON string using `Json { encodeDefaults = true }.encodeToString(SynthState.serializer(), state)`
- [x] 1.2 Replace each fallback preset's `parametersJson = "{}"` with real parameter JSON matching the preset name and description
- [x] 1.3 For each preset, create plausible parameter variations (vary osc waveforms, filter cutoff, envelope shapes, effects)
- [x] 1.4 Build and verify fallback presets change the sound when selected

## 2. Add User Feedback on Preset Load

- [x] 2.1 In MainSynthScreen.kt onSelectPreset, add `Toast.makeText(context, "Loaded: ${preset.name}", Toast.LENGTH_SHORT).show()` after successful load
- [x] 2.2 Add `import android.widget.Toast`
- [x] 2.3 Remove the duplicate `dbPresets` LaunchedEffect (line 62-65) since PresetBrowser manages its own data
- [x] 2.4 Build and verify toast appears when a preset is loaded

## 3. Quality Gate

- [x] 3.1 Run `./gradlew lint`
- [x] 3.2 Run `./gradlew clean assembleDebug`
