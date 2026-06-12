## 1. Fix Keyboard Rendering

- [ ] 1.1 In KeyboardView.kt KeyboardKeys, add `.clip(RoundedCornerShape(4.dp)).background(BgKnobArea)` back to the scrollable Box
- [ ] 1.2 Replace `Canvas(modifier = Modifier.fillMaxSize())` with `Canvas(modifier = Modifier.width(keyboardWidth).fillMaxHeight())` so the Canvas is explicitly 3× width instead of filling the viewport
- [ ] 1.3 Increase white key label font from 9sp to 11sp for readability
- [ ] 1.4 Build and verify

## 2. Fix Preset Stutter

- [ ] 2.1 In MainSynthScreen.kt applySynthStateToEngine, REMOVE the `SynthEngine.setParam(60, state.sequencerTempo)` and `SyncEngine.setParam(61, ...)` calls — presets should NOT control the sequencer
- [ ] 2.2 In AudioEngine.cpp, ensure `noteOn()` always calls `applyModulationMatrix()` BEFORE setting the voice active (so new notes get full params)
- [ ] 2.3 Build and verify
