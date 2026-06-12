## 1. Fix C++ Audio Engine

- [x] 1.1 In AudioEngine.cpp, rewrite `noteOn()` to iterate all voices and `stopImmediately()` any playing the same MIDI note before allocating a new voice
- [x] 1.2 In AudioEngine.cpp, rewrite `noteOff()` to iterate all voices and call `noteOff()` on EVERY voice playing the MIDI note (not just the first match)
- [x] 1.3 Build and verify

## 2. Quality Gate

- [x] 2.1 Run `./gradlew clean assembleDebug`
