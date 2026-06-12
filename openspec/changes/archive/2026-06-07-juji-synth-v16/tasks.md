## 1. Fix Ambient Presets

- [x] 1.1 In PresetBrowser.kt, change "Washy Pad": `ampAttack = 0.7f` → `0.3f`, `delayMix = 0.3f` → `0f`, remove delayTime/delayFeedback
- [x] 1.2 Change "Deep Space": `ampAttack = 0.8f` → `0.35f`, `delayMix = 0.25f` → `0f`, remove delayFeedback
- [x] 1.3 Change "Ethereal Haze": `ampAttack = 0.6f` → `0.25f`, `delayMix = 0.35f` → `0f`, remove delayTime/delayFeedback
- [x] 1.4 Change "Evolving Pad": `ampAttack = 0.6f` → `0.4f` (still slow but more responsive)
- [x] 1.5 Build and verify

## 2. Quality Gate

- [x] 2.1 Run `./gradlew lint`
- [x] 2.2 Run `./gradlew clean assembleDebug`